package ru.tochka.mood;

import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.io.*;
import java.util.*;
import org.json.*;

/** Dependency-free device tests against isolated databases, never the user's diary. */
public final class StoreInstrumentation extends Instrumentation {
  private interface Case {
    void run() throws Exception;
  }

  private int count, failed;
  private String failure = "";

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    start();
  }

  private MoodStore fresh() {
    return new MoodStore(getTargetContext(), "test-" + UUID.randomUUID() + ".db");
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private void test(String name, Case action) {
    Bundle status = new Bundle();
    status.putString("class", getClass().getName());
    status.putString("test", name);
    status.putInt("numtests", 10);
    status.putInt("current", ++count);
    sendStatus(1, status);
    try {
      action.run();
      sendStatus(0, status);
    } catch (Throwable ex) {
      failed++;
      failure += name + ": " + ex + "\n";
      status.putString("stack", ex.toString());
      sendStatus(-2, status);
    }
  }

  @Override
  public void onStart() {
    test(
        "corruptionDoesNotDeleteDiary",
        () -> {
          String name = "test-corruption-" + UUID.randomUUID() + ".db";
          File file = getTargetContext().getDatabasePath(name);
          file.getParentFile().mkdirs();
          byte[] content =
              "This file is intentionally not a SQLite database."
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          try (OutputStream output = new FileOutputStream(file)) {
            output.write(content);
          }
          boolean rejected = false;
          try (MoodStore s = new MoodStore(getTargetContext(), name)) {
            s.getReadableDatabase();
          } catch (android.database.sqlite.SQLiteException ex) {
            rejected = true;
          }
          check(
              rejected && file.exists() && file.length() == content.length,
              "corrupt diary retained");
        });
    test(
        "exportFailureReleasesTransaction",
        () -> {
          try (MoodStore s = fresh()) {
            s.add(s.slots().get(0));
            boolean rejected = false;
            try {
              BackupCodec.write(
                  s,
                  new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                      throw new IOException("Synthetic full storage");
                    }
                  });
            } catch (IOException ex) {
              rejected = true;
            }
            check(rejected, "export reports IO failure");
            s.add(s.slots().get(0));
            check(s.count() == 2, "transaction released without data loss");
          }
        });
    test(
        "freshDefaultsAndForeignKeys",
        () -> {
          try (MoodStore s = fresh()) {
            check(s.slots().size() == 9, "nine slots");
            check(s.slots().get(4).key.equals("NEUTRAL"), "neutral centre");
            check(s.count() == 0, "empty diary");
            boolean rejected = false;
            try {
              s.getWritableDatabase()
                  .execSQL("INSERT INTO slots(position,emotion_key) VALUES(10,'missing')");
            } catch (Exception ex) {
              rejected = true;
            }
            check(rejected, "invalid slot rejected");
          }
        });
    test(
        "immutableRevisionsAndStatistics",
        () -> {
          try (MoodStore s = fresh()) {
            Emotion original = s.slots().get(6);
            long old = s.add(original);
            s.update(old, original, "old comment");
            s.replaceSlot(6, "Усталость", "Z", Emotion.COLORS[1]);
            Emotion changed = s.slots().get(6);
            s.add(changed);
            check(!original.key.equals(changed.key), "new identity");
            check(s.get(old).emotion.title.equals("Тревога"), "old name");
            check(s.get(old).emotion.symbol.equals(original.symbol), "old symbol");
            check(s.get(old).comment.equals("old comment"), "old comment");
            MoodStore.Statistics stats = s.statistics(0);
            check(
                stats.total == 2
                    && stats.counts.get(original.key) == 1
                    && stats.counts.get(changed.key) == 1,
                "separate counts");
            check(!stats.current.contains(original.key), "archived");
            s.update(old, s.get(old).emotion, "updated comment");
            check(s.get(old).emotion.key.equals(original.key), "comment keeps original emotion");
            s.replaceSlot(6, changed.title, changed.symbol, changed.color);
            check(s.slots().get(6).key.equals(changed.key), "no-op stable");
            check(s.statistics(Long.MAX_VALUE).total == 0, "period boundary");
          }
        });
    test(
        "versionOneMigration",
        () -> {
          String name = "test-migration1-" + UUID.randomUUID() + ".db";
          try (SQLiteDatabase db = getTargetContext().openOrCreateDatabase(name, 0, null)) {
            db.execSQL(
                "CREATE TABLE entries(_id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER NOT"
                    + " NULL,emotion TEXT NOT NULL,comment TEXT NOT NULL DEFAULT '')");
            db.execSQL(
                "INSERT INTO entries(time,emotion,comment) VALUES(123456,'ANXIETY','сохранить')");
            db.setVersion(1);
          }
          try (MoodStore s = new MoodStore(getTargetContext(), name)) {
            check(s.slots().size() == 9 && s.count() == 1, "migration keeps data");
            check(
                s.get(1).comment.equals("сохранить") && s.get(1).time == 123456,
                "migration values");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            BackupCodec.write(s, output);
            BackupCodec.read(new ByteArrayInputStream(output.toByteArray()));
          }
        });
    test(
        "versionTwoMigration",
        () -> {
          String name = "test-migration2-" + UUID.randomUUID() + ".db";
          try (SQLiteDatabase db = getTargetContext().openOrCreateDatabase(name, 0, null)) {
            db.execSQL(
                "CREATE TABLE entries(_id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER NOT"
                    + " NULL,emotion TEXT NOT NULL,comment TEXT NOT NULL DEFAULT '')");
            db.execSQL(
                "CREATE TABLE emotions(emotion_key TEXT PRIMARY KEY,title TEXT NOT NULL,symbol TEXT"
                    + " NOT NULL,color INTEGER NOT NULL)");
            db.execSQL(
                "CREATE TABLE slots(position INTEGER PRIMARY KEY,emotion_key TEXT NOT NULL)");
            for (int i = 0; i < 9; i++) {
              Emotion e = Emotion.defaults()[i];
              db.execSQL(
                  "INSERT INTO emotions VALUES(?,?,?,?)",
                  new Object[] {e.key, e.title, e.symbol, e.color});
              db.execSQL("INSERT INTO slots VALUES(?,?)", new Object[] {i, e.key});
            }
            db.execSQL("INSERT INTO entries(time,emotion,comment) VALUES(234567,'JOY','v2')");
            db.setVersion(2);
          }
          try (MoodStore s = new MoodStore(getTargetContext(), name)) {
            check(s.count() == 1 && s.get(1).comment.equals("v2"), "v2 migration preserves entry");
            s.add(s.slots().get(0));
            check(s.count() == 2, "v2 can append");
          }
        });
    test(
        "backupMergeAndRepeatedImport",
        () -> {
          try (MoodStore source = fresh();
              MoodStore target = fresh()) {
            Emotion original = source.slots().get(6);
            source.add(original);
            source.replaceSlot(6, "Усталость", "Z", Emotion.COLORS[1]);
            source.add(source.slots().get(6));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            BackupCodec.write(source, output);
            JSONObject backup = BackupCodec.read(new ByteArrayInputStream(output.toByteArray()));
            check(BackupCodec.restore(target, backup) == 2, "import count");
            check(BackupCodec.restore(target, backup) == 0 && target.count() == 2, "idempotent");
            MoodStore.Entry entry = target.latest();
            target.update(entry.id, entry.emotion, "local comment");
            BackupCodec.restore(target, backup);
            check(target.get(entry.id).comment.equals("local comment"), "local edits retained");
            check(target.slots().get(6).title.equals("Усталость"), "slots restored");
            check(target.statistics(0).counts.get(original.key) == 1, "archived restored");
          }
        });
    test(
        "invalidBackupIsAtomic",
        () -> {
          try (MoodStore s = fresh()) {
            s.add(s.slots().get(0));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BackupCodec.write(s, bytes);
            JSONObject backup = BackupCodec.read(new ByteArrayInputStream(bytes.toByteArray()));
            JSONObject extra =
                new JSONObject()
                    .put("key", "new-test-revision")
                    .put("title", "Test")
                    .put("symbol", "T")
                    .put("color", Emotion.COLORS[0]);
            backup.getJSONArray("emotions").put(0, extra);
            // The duplicated catalogue key fails validation before any write.
            backup.getJSONArray("slots").put(0, "missing");
            boolean rejected = false;
            try {
              BackupCodec.restore(s, backup);
            } catch (Exception ex) {
              rejected = true;
            }
            check(
                rejected && s.count() == 1 && s.emotion("new-test-revision") == null,
                "no partial import");
            bytes.reset();
            BackupCodec.write(s, bytes);
            backup = BackupCodec.read(new ByteArrayInputStream(bytes.toByteArray()));
            JSONArray oldCatalog = backup.getJSONArray("emotions");
            oldCatalog.getJSONObject(0).put("title", "Conflict");
            JSONArray reordered = new JSONArray().put(extra);
            for (int i = 0; i < oldCatalog.length(); i++) reordered.put(oldCatalog.get(i));
            backup.put("emotions", reordered);
            rejected = false;
            try {
              BackupCodec.restore(s, backup);
            } catch (Exception ex) {
              rejected = true;
            }
            check(
                rejected && s.count() == 1 && s.emotion("new-test-revision") == null,
                "transaction rolls back partial catalogue");
          }
        });
    test(
        "boundedHistoryAndValidation",
        () -> {
          try (MoodStore s = fresh()) {
            Emotion e = s.slots().get(0);
            for (int i = 0; i < 125; i++) s.add(e);
            check(s.page(0, 50).size() == 50 && s.page(100, 50).size() == 25, "pagination");
            check(s.page(0, 50).get(49).id != s.page(50, 50).get(0).id, "non-overlap");
            String key = s.slots().get(0).key;
            boolean rejected = false;
            try {
              s.replaceSlot(0, " ", "X", e.color);
            } catch (Exception ex) {
              rejected = true;
            }
            check(rejected && key.equals(s.slots().get(0).key), "invalid slot leaves data");
          }
        });
    test(
        "oversizedInputRejected",
        () -> {
          InputStream input =
              new InputStream() {
                int left = BackupCodec.MAX_BYTES + 1;

                public int read() {
                  return left-- > 0 ? 32 : -1;
                }

                public int read(byte[] b, int off, int len) {
                  if (left <= 0) return -1;
                  int n = Math.min(len, left);
                  Arrays.fill(b, off, off + n, (byte) 32);
                  left -= n;
                  return n;
                }
              };
          boolean rejected = false;
          try {
            BackupCodec.read(input);
          } catch (IOException ex) {
            rejected = true;
          }
          check(rejected, "size cap");
        });
    Bundle results = new Bundle();
    results.putString(
        "stream", failed == 0 ? "\nOK (" + count + " tests)\n" : "\nFAILURES!!!\n" + failure);
    finish(failed == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, results);
  }
}
