package ru.tochka.mood;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public final class MoodStore extends SQLiteOpenHelper {
  public static final class Entry {
    public final long id, time;
    public final Emotion emotion;
    public final String comment;

    Entry(long id, long time, Emotion emotion, String comment) {
      this.id = id;
      this.time = time;
      this.emotion = emotion;
      this.comment = comment;
    }
  }

  public MoodStore(Context c) {
    this(c, "moods.db");
  }

  MoodStore(Context c, String name) {
    super(
        c,
        name,
        null,
        3,
        db -> {
          throw new android.database.sqlite.SQLiteDatabaseCorruptException(
              "Diary database is corrupt; automatic deletion disabled");
        });
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE entries (_id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER NOT NULL,emotion"
            + " TEXT NOT NULL REFERENCES emotions(emotion_key),comment TEXT NOT NULL DEFAULT"
            + " '',uuid TEXT NOT NULL)");
    db.execSQL("CREATE INDEX entries_time ON entries(time,_id)");
    db.execSQL("CREATE UNIQUE INDEX entries_uuid ON entries(uuid)");
    createCatalog(db);
  }

  private void createCatalog(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE emotions (emotion_key TEXT PRIMARY KEY,title TEXT NOT NULL,symbol TEXT NOT"
            + " NULL,color INTEGER NOT NULL)");
    db.execSQL(
        "CREATE TABLE slots (position INTEGER PRIMARY KEY CHECK(position BETWEEN 0 AND"
            + " 8),emotion_key TEXT NOT NULL REFERENCES emotions(emotion_key))");
    Emotion[] defaults = Emotion.defaults();
    for (int i = 0; i < defaults.length; i++) {
      insertEmotion(db, defaults[i]);
      ContentValues slot = new ContentValues();
      slot.put("position", i);
      slot.put("emotion_key", defaults[i].key);
      db.insertOrThrow("slots", null, slot);
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
    // The helper wraps migration in a transaction; old entry keys match defaults.
    if (oldV == 1) createCatalog(db);
    if (oldV < 3) {
      db.execSQL("ALTER TABLE entries ADD COLUMN uuid TEXT");
      db.execSQL("UPDATE entries SET uuid=lower(hex(randomblob(16))) WHERE uuid IS NULL");
      db.execSQL("CREATE UNIQUE INDEX entries_uuid ON entries(uuid)");
    }
  }

  private void insertEmotion(SQLiteDatabase db, Emotion e) {
    ContentValues v = new ContentValues();
    v.put("emotion_key", e.key);
    v.put("title", e.title);
    v.put("symbol", e.symbol);
    v.put("color", e.color);
    db.insertOrThrow("emotions", null, v);
  }

  public List<Emotion> slots() {
    List<Emotion> result = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT e.emotion_key,e.title,e.symbol,e.color FROM slots s JOIN emotions e ON"
                    + " e.emotion_key=s.emotion_key ORDER BY s.position",
                null)) {
      while (c.moveToNext())
        result.add(new Emotion(c.getString(0), c.getString(1), c.getString(2), c.getInt(3)));
    }
    return result;
  }

  public void replaceSlot(int position, String title, String symbol, int color) {
    title = title.trim();
    symbol = symbol.trim();
    boolean validColor = false;
    for (int c : Emotion.COLORS) if (c == color) validColor = true;
    if (position < 0
        || position > 8
        || title.isEmpty()
        || title.length() > 24
        || symbol.isEmpty()
        || symbol.length() > 12
        || !validColor) throw new IllegalArgumentException("Invalid emotion");
    Emotion old = slots().get(position);
    if (old.title.equals(title) && old.symbol.equals(symbol) && old.color == color) return;
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      Emotion revision = new Emotion(UUID.randomUUID().toString(), title, symbol, color);
      insertEmotion(db, revision);
      ContentValues v = new ContentValues();
      v.put("emotion_key", revision.key);
      db.update("slots", v, "position=?", new String[] {String.valueOf(position)});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public Emotion emotion(String key) {
    if (key == null) return null;
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT emotion_key,title,symbol,color FROM emotions WHERE emotion_key=?",
                new String[] {key})) {
      return c.moveToFirst()
          ? new Emotion(c.getString(0), c.getString(1), c.getString(2), c.getInt(3))
          : null;
    }
  }

  public long add(Emotion e) {
    if (e == null || emotion(e.key) == null) throw new IllegalArgumentException("Unknown emotion");
    ContentValues v = new ContentValues();
    v.put("time", System.currentTimeMillis());
    v.put("emotion", e.key);
    v.put("uuid", UUID.randomUUID().toString());
    return getWritableDatabase().insertOrThrow("entries", null, v);
  }

  public void update(long id, Emotion e, String comment) {
    if (e == null || emotion(e.key) == null || comment == null || comment.length() > 10000)
      throw new IllegalArgumentException("Invalid entry");
    ContentValues v = new ContentValues();
    v.put("emotion", e.key);
    v.put("comment", comment);
    if (getWritableDatabase().update("entries", v, "_id=?", new String[] {String.valueOf(id)}) != 1)
      throw new IllegalArgumentException("Entry no longer exists");
  }

  public void delete(long id) {
    getWritableDatabase().delete("entries", "_id=?", new String[] {String.valueOf(id)});
  }

  private static final String QUERY =
      "SELECT n._id,n.time,n.comment,e.emotion_key,e.title,e.symbol,e.color FROM entries n JOIN"
          + " emotions e ON n.emotion=e.emotion_key ";

  private Entry read(Cursor c) {
    return new Entry(
        c.getLong(0),
        c.getLong(1),
        new Emotion(c.getString(3), c.getString(4), c.getString(5), c.getInt(6)),
        c.getString(2));
  }

  public List<Entry> entries(long since) {
    List<Entry> result = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                QUERY + "WHERE n.time>=? ORDER BY n.time DESC,n._id DESC",
                new String[] {String.valueOf(since)})) {
      while (c.moveToNext()) result.add(read(c));
    }
    return result;
  }

  public List<Entry> page(int offset, int limit) {
    List<Entry> result = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                QUERY + "ORDER BY n.time DESC,n._id DESC LIMIT ? OFFSET ?",
                new String[] {String.valueOf(limit), String.valueOf(offset)})) {
      while (c.moveToNext()) result.add(read(c));
    }
    return result;
  }

  public long count() {
    return android.database.DatabaseUtils.longForQuery(
        getReadableDatabase(), "SELECT count(*) FROM entries", null);
  }

  public static final class Statistics {
    public final Map<String, Integer> counts = new LinkedHashMap<>();
    public final Map<String, Emotion> emotions = new LinkedHashMap<>();
    public final Set<String> current = new HashSet<>();
    public int total, activeDays;
  }

  public Statistics statistics(long since) {
    Statistics s = new Statistics();
    for (Emotion e : slots()) {
      s.emotions.put(e.key, e);
      s.current.add(e.key);
    }
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT e.emotion_key,e.title,e.symbol,e.color,count(*) FROM entries n JOIN"
                    + " emotions e ON n.emotion=e.emotion_key WHERE n.time>=? GROUP BY"
                    + " e.emotion_key ORDER BY max(n.time) DESC",
                new String[] {String.valueOf(since)})) {
      while (c.moveToNext()) {
        Emotion e = new Emotion(c.getString(0), c.getString(1), c.getString(2), c.getInt(3));
        s.emotions.put(e.key, e);
        s.counts.put(e.key, c.getInt(4));
        s.total += c.getInt(4);
      }
    }
    s.activeDays =
        (int)
            android.database.DatabaseUtils.longForQuery(
                getReadableDatabase(),
                "SELECT count(DISTINCT date(time/1000,'unixepoch','localtime')) FROM entries WHERE"
                    + " time>=?",
                new String[] {String.valueOf(since)});
    return s;
  }

  public Entry get(long id) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(QUERY + "WHERE n._id=?", new String[] {String.valueOf(id)})) {
      return c.moveToFirst() ? read(c) : null;
    }
  }

  public Entry latest() {
    try (Cursor c =
        getReadableDatabase().rawQuery(QUERY + "ORDER BY n.time DESC,n._id DESC LIMIT 1", null)) {
      return c.moveToFirst() ? read(c) : null;
    }
  }
}
