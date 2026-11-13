package ru.tochka.mood;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.RemoteViews;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MoodWidget extends AppWidgetProvider {
  private static final String RECORD = "ru.tochka.mood.RECORD";
  private static final int[] CELLS = {
    R.id.cell0,
    R.id.cell1,
    R.id.cell2,
    R.id.cell3,
    R.id.cell4,
    R.id.cell5,
    R.id.cell6,
    R.id.cell7,
    R.id.cell8
  };
  private static final Map<Integer, Long> confirmations =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.concurrent.ExecutorService WRITER =
      java.util.concurrent.Executors.newSingleThreadExecutor();

  @Override
  public void onReceive(Context context, Intent intent) {
    super.onReceive(context, intent);
    if (!RECORD.equals(intent.getAction())) return;
    Context c = context.getApplicationContext();
    int slot = intent.getIntExtra("slot", -1), widget = intent.getIntExtra("widget", -1);
    if (slot < 0 || slot > 8 || widget < 0) return;
    String key = intent.getStringExtra("emotion_key");
    PendingResult pending = goAsync();
    Handler main = new Handler(Looper.getMainLooper());
    WRITER.execute(
        () -> {
          final long entryId;
          try (MoodStore store = new MoodStore(c)) {
            if (AppWidgetManager.getInstance(c).getAppWidgetInfo(widget) == null) {
              pending.finish();
              return;
            }
            Emotion selected = store.emotion(key);
            if (selected == null) {
              pending.finish();
              return;
            }
            entryId = store.add(selected);
          } catch (RuntimeException ex) {
            main.post(
                () ->
                    android.widget.Toast.makeText(
                            c, "Не удалось сохранить отметку", android.widget.Toast.LENGTH_LONG)
                        .show());
            pending.finish();
            return;
          }
          main.post(
              () -> {
                try {
                  confirmations.put(widget, entryId);
                  refreshAll(c);
                } catch (RuntimeException ex) {
                  confirmations.remove(widget);
                  pending.finish();
                  return;
                }
                // Keep process lifetime through the short timer without blocking the UI.
                main.postDelayed(
                    () -> {
                      try {
                        if (confirmations.remove(widget, entryId))
                          update(c, AppWidgetManager.getInstance(c), widget);
                      } catch (RuntimeException ignored) {
                        /* An entry is already safely stored; retry on next widget refresh. */
                      } finally {
                        pending.finish();
                      }
                    },
                    2800);
              });
        });
  }

  @Override
  public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
    for (int id : ids) update(c, manager, id);
  }

  @Override
  public void onAppWidgetOptionsChanged(
      Context c, AppWidgetManager manager, int id, Bundle options) {
    update(c, manager, id);
  }

  @Override
  public void onDeleted(Context c, int[] ids) {
    for (int id : ids) confirmations.remove(id);
  }

  static void refreshAll(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    for (Class<?> cls : new Class<?>[] {MoodWidget.class, LargeMoodWidget.class})
      for (int id : m.getAppWidgetIds(new ComponentName(c, cls))) update(c, m, id);
  }

  private static PendingIntent open(Context c, long entryId) {
    Intent intent =
        new Intent(c, MainActivity.class)
            .setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setData(android.net.Uri.parse("mood://entry/" + entryId));
    if (entryId > 0) intent.putExtra("entry_id", entryId);
    return PendingIntent.getActivity(
        c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  static void update(Context c, AppWidgetManager manager, int id) {
    if (manager.getAppWidgetInfo(id) == null) return;
    boolean compact =
        manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
            < 220;
    RemoteViews v =
        new RemoteViews(
            c.getPackageName(), compact ? R.layout.widget_compact : R.layout.widget_full);
    try (MoodStore store = new MoodStore(c)) {
      List<Emotion> emotions = store.slots();
      int[] backgrounds = {
        R.drawable.cell_0,
        R.drawable.cell_1,
        R.drawable.cell_2,
        R.drawable.cell_3,
        R.drawable.cell_4,
        R.drawable.cell_5,
        R.drawable.cell_6,
        R.drawable.cell_7,
        R.drawable.cell_8
      };
      for (int i = 0; i < emotions.size(); i++) {
        Emotion e = emotions.get(i);
        android.text.SpannableString label =
            new android.text.SpannableString(e.symbol + (compact ? "" : "\n" + e.title));
        if (!compact)
          label.setSpan(
              new android.text.style.AbsoluteSizeSpan(24, true),
              0,
              e.symbol.length(),
              android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.setTextViewText(CELLS[i], label);
        v.setContentDescription(CELLS[i], e.title + " — записать эмоцию");
        int colorIndex = 0;
        for (int n = 0; n < Emotion.COLORS.length; n++)
          if (e.color == Emotion.COLORS[n]) colorIndex = n;
        v.setInt(CELLS[i], "setBackgroundResource", backgrounds[colorIndex]);
        Intent record =
            new Intent(c, MoodWidget.class)
                .setAction(RECORD)
                .putExtra("slot", i)
                .putExtra("emotion_key", e.key)
                .putExtra("widget", id)
                .setData(android.net.Uri.parse("mood://record/" + id + "/" + i + "/" + e.key));
        v.setOnClickPendingIntent(
            CELLS[i],
            PendingIntent.getBroadcast(
                c, 0, record, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
      }
      v.setOnClickPendingIntent(R.id.widget_header, open(c, 0));
      MoodStore.Entry latest = store.latest();
      v.setTextViewText(
          R.id.widget_status,
          latest == null
              ? "Нажми на эмоцию"
              : latest.emotion.title
                  + " · "
                  + DateTimeFormatter.ofPattern("dd.MM HH:mm")
                      .format(Instant.ofEpochMilli(latest.time).atZone(ZoneId.systemDefault())));
      Long selected = confirmations.get(id);
      MoodStore.Entry entry = selected == null ? null : store.get(selected);
      v.setViewVisibility(R.id.widget_grid, entry == null ? View.VISIBLE : View.GONE);
      v.setViewVisibility(R.id.confirmation, entry == null ? View.GONE : View.VISIBLE);
      if (entry != null) {
        v.setTextViewText(R.id.confirm_symbol, entry.emotion.symbol);
        v.setTextViewText(R.id.confirm_title, "Выбрано: " + entry.emotion.title);
        v.setOnClickPendingIntent(R.id.confirmation, open(c, entry.id));
        v.setContentDescription(
            R.id.confirmation, "Выбрано: " + entry.emotion.title + ". Добавить комментарий");
      }
    }
    manager.updateAppWidget(id, v);
  }
}
