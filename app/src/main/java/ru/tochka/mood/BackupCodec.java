package ru.tochka.mood;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Versioned, bounded plaintext backups. Import is validated before one transaction. */
final class BackupCodec {
  static final int MAX_BYTES = 16 * 1024 * 1024, MAX_ENTRIES = 100000;

  static void write(MoodStore store, OutputStream output) throws Exception {
    OutputStream bounded =
        new FilterOutputStream(output) {
          private int written;

          @Override
          public void write(int value) throws IOException {
            if (++written > MAX_BYTES) throw new IOException("Копия превышает 16 МБ");
            out.write(value);
          }

          @Override
          public void write(byte[] data, int offset, int length) throws IOException {
            if (written + length > MAX_BYTES) throw new IOException("Копия превышает 16 МБ");
            written += length;
            out.write(data, offset, length);
          }
        };
    android.util.JsonWriter writer =
        new android.util.JsonWriter(new OutputStreamWriter(bounded, StandardCharsets.UTF_8));
    SQLiteDatabase db = store.getReadableDatabase();
    db.beginTransaction();
    try {
      if (store.count() > MAX_ENTRIES)
        throw new IOException("Слишком много записей для одной копии");
      writer
          .beginObject()
          .name("format")
          .value("nine-feelings")
          .name("version")
          .value(1)
          .name("emotions")
          .beginArray();
      try (Cursor c =
          db.rawQuery(
              "SELECT emotion_key,title,symbol,color FROM emotions ORDER BY emotion_key", null)) {
        while (c.moveToNext())
          writer
              .beginObject()
              .name("key")
              .value(c.getString(0))
              .name("title")
              .value(c.getString(1))
              .name("symbol")
              .value(c.getString(2))
              .name("color")
              .value(c.getInt(3))
              .endObject();
      }
      writer.endArray().name("slots").beginArray();
      for (Emotion e : store.slots()) writer.value(e.key);
      writer.endArray().name("entries").beginArray();
      try (Cursor c =
          db.rawQuery("SELECT uuid,time,emotion,comment FROM entries ORDER BY time,_id", null)) {
        if (c.getCount() > MAX_ENTRIES)
          throw new IOException("Слишком много записей для одной копии");
        while (c.moveToNext())
          writer
              .beginObject()
              .name("uuid")
              .value(c.getString(0))
              .name("time")
              .value(c.getLong(1))
              .name("emotion")
              .value(c.getString(2))
              .name("comment")
              .value(c.getString(3))
              .endObject();
      }
      writer.endArray().endObject();
      writer.flush();
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  static JSONObject read(InputStream input) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int n;
    while ((n = input.read(chunk)) != -1) {
      if (bytes.size() + n > MAX_BYTES) throw new IOException("Файл превышает 16 МБ");
      bytes.write(chunk, 0, n);
    }
    JSONObject root = new JSONObject(bytes.toString("UTF-8"));
    validate(root);
    return root;
  }

  private static String text(JSONObject o, String field, int max) throws JSONException {
    Object raw = o.get(field);
    if (!(raw instanceof String)) throw new JSONException("Неверное поле " + field);
    String value = (String) raw;
    if (value.isEmpty() || value.length() > max) throw new JSONException("Неверное поле " + field);
    return value;
  }

  static void validate(JSONObject root) throws JSONException {
    if (!"nine-feelings".equals(root.getString("format")) || root.getInt("version") != 1)
      throw new JSONException("Неизвестный формат копии");
    JSONArray emotions = root.getJSONArray("emotions"),
        slots = root.getJSONArray("slots"),
        entries = root.getJSONArray("entries");
    if (emotions.length() > MAX_ENTRIES || entries.length() > MAX_ENTRIES || slots.length() != 9)
      throw new JSONException("Неверный размер копии");
    Set<String> keys = new HashSet<>(), uuids = new HashSet<>();
    for (int i = 0; i < emotions.length(); i++) {
      JSONObject e = emotions.getJSONObject(i);
      String key = text(e, "key", 128);
      if (!keys.add(key)) throw new JSONException("Повтор эмоции");
      if (text(e, "title", 24).trim().isEmpty() || text(e, "symbol", 12).trim().isEmpty())
        throw new JSONException("Пустая эмоция");
      int color = e.getInt("color");
      boolean valid = false;
      for (int c : Emotion.COLORS) if (c == color) valid = true;
      if (!valid) throw new JSONException("Неизвестный цвет");
    }
    for (int i = 0; i < 9; i++)
      if (!keys.contains(slots.getString(i))) throw new JSONException("Неизвестная ячейка");
    for (int i = 0; i < entries.length(); i++) {
      JSONObject e = entries.getJSONObject(i);
      String uuid = text(e, "uuid", 64);
      if (!uuid.matches("[a-zA-Z0-9-]{16,64}") || !uuids.add(uuid))
        throw new JSONException("Неверный идентификатор записи");
      long time = e.getLong("time");
      if (time < 0 || time > 253402300799999L || !keys.contains(e.getString("emotion")))
        throw new JSONException("Неверная запись");
      if (!(e.get("comment") instanceof String) || e.getString("comment").length() > 10000)
        throw new JSONException("Слишком длинный комментарий");
    }
  }

  static int restore(MoodStore store, JSONObject root) throws Exception {
    validate(root);
    SQLiteDatabase db = store.getWritableDatabase();
    db.beginTransaction();
    int added = 0;
    try {
      JSONArray emotions = root.getJSONArray("emotions"),
          entries = root.getJSONArray("entries"),
          slots = root.getJSONArray("slots");
      for (int i = 0; i < emotions.length(); i++) {
        JSONObject e = emotions.getJSONObject(i);
        String key = e.getString("key");
        Emotion old = store.emotion(key);
        if (old != null) {
          if (!old.title.equals(e.getString("title"))
              || !old.symbol.equals(e.getString("symbol"))
              || old.color != e.getInt("color")) throw new JSONException("Конфликт версий эмоции");
          continue;
        }
        ContentValues v = new ContentValues();
        v.put("emotion_key", key);
        v.put("title", e.getString("title"));
        v.put("symbol", e.getString("symbol"));
        v.put("color", e.getInt("color"));
        db.insertOrThrow("emotions", null, v);
      }
      for (int i = 0; i < entries.length(); i++) {
        JSONObject e = entries.getJSONObject(i);
        String uuid = e.getString("uuid");
        if (android.database.DatabaseUtils.longForQuery(
                db, "SELECT count(*) FROM entries WHERE uuid=?", new String[] {uuid})
            != 0) continue;
        ContentValues v = new ContentValues();
        v.put("uuid", uuid);
        v.put("time", e.getLong("time"));
        v.put("emotion", e.getString("emotion"));
        v.put("comment", e.getString("comment"));
        db.insertOrThrow("entries", null, v);
        added++;
      }
      for (int i = 0; i < 9; i++) {
        ContentValues v = new ContentValues();
        v.put("emotion_key", slots.getString(i));
        db.update("slots", v, "position=?", new String[] {String.valueOf(i)});
      }
      db.setTransactionSuccessful();
      return added;
    } finally {
      db.endTransaction();
    }
  }
}
