package ru.tochka.mood;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
  private static final int BG = 0xFF12151A,
      INK = 0xFFEEF2F6,
      MUTED = 0xFFA6B0BE,
      ACCENT = 0xFFC5B5FF;

  private static int emotionSurface(int color) {
    return Color.rgb(
        Math.round(Color.red(color) * .22f + 29 * .78f),
        Math.round(Color.green(color) * .22f + 34 * .78f),
        Math.round(Color.blue(color) * .22f + 41 * .78f));
  }

  private MoodStore store;
  private LinearLayout root, content;
  private String tab = "history";
  private int days = 7;
  private int historyPage = 0;
  private AlertDialog editor;
  private String editorKind;
  private long editorEntry;
  private int editorSlot;
  private EditText editorText, editorSymbol;
  private Spinner editorSpinner;
  private boolean loaded = false;

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  private GradientDrawable bg(int color, int radius) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radius));
    return g;
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private void space(LinearLayout l, int h) {
    View v = new View(this);
    l.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
  }

  private TextView button(String s, Runnable action) {
    TextView t = text(s, 14, INK);
    t.setGravity(Gravity.CENTER);
    t.setMinHeight(dp(48));
    t.setMinWidth(dp(48));
    t.setPadding(dp(12), dp(12), dp(12), dp(12));
    t.setBackground(bg(0xFF282F38, 18));
    t.setOnClickListener(v -> action.run());
    t.setFocusable(true);
    t.setAccessibilityDelegate(
        new View.AccessibilityDelegate() {
          @Override
          public void onInitializeAccessibilityNodeInfo(
              View host, android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName("android.widget.Button");
          }
        });
    return t;
  }

  private void weighted(LinearLayout r, View v) {
    LinearLayout.LayoutParams p =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    p.setMargins(dp(3), 0, dp(3), 0);
    r.addView(v, p);
  }

  private void postUi(Runnable action) {
    new android.os.Handler(android.os.Looper.getMainLooper())
        .post(
            () -> {
              if (!isDestroyed() && !isFinishing()) {
                try {
                  action.run();
                } catch (android.database.sqlite.SQLiteException ex) {
                  showStorageError();
                }
              }
            });
  }

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    store = new MoodStore(this);
    if (b != null) {
      tab = b.getString("tab", "history");
      days = b.getInt("days", 7);
      historyPage = b.getInt("page", 0);
    }
    render();
    loaded = true;
    try {
      MoodWidget.refreshAll(this);
    } catch (android.database.sqlite.SQLiteException ex) {
      showStorageError();
    }
    postUi(
        () -> {
          if (b != null && b.containsKey("editor_kind")) restoreEditor(b);
          else openRequestedEntry();
        });
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (editor != null) editor.dismiss();
    setIntent(intent);
    tab = "history";
    historyPage = 0;
    render();
    postUi(this::openRequestedEntry);
  }

  private void openRequestedEntry() {
    long id = getIntent().getLongExtra("entry_id", -1);
    getIntent().removeExtra("entry_id");
    if (id > 0) {
      MoodStore.Entry entry = store.get(id);
      if (entry != null) edit(entry);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (loaded) render();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("tab", tab);
    out.putInt("days", days);
    out.putInt("page", historyPage);
    if (editor != null && editor.isShowing()) {
      out.putString("editor_kind", editorKind);
      out.putLong("editor_entry", editorEntry);
      out.putInt("editor_slot", editorSlot);
      out.putString("editor_text", editorText.getText().toString());
      out.putInt("editor_selection", editorSpinner.getSelectedItemPosition());
      if (editorSymbol != null) out.putString("editor_symbol", editorSymbol.getText().toString());
    }
  }

  private void restoreEditor(Bundle b) {
    if ("entry".equals(b.getString("editor_kind"))) {
      MoodStore.Entry e = store.get(b.getLong("editor_entry"));
      if (e == null) return;
      edit(e);
    } else editSlot(b.getInt("editor_slot"));
    editorText.setText(b.getString("editor_text", ""));
    if (editorSymbol != null) editorSymbol.setText(b.getString("editor_symbol", ""));
    if (b.getInt("editor_selection", 0) < editorSpinner.getCount())
      editorSpinner.setSelection(b.getInt("editor_selection", 0));
  }

  @Override
  public void onDestroy() {
    if (editor != null) editor.dismiss();
    store.close();
    super.onDestroy();
  }

  private void render() {
    try {
      renderContent();
    } catch (android.database.sqlite.SQLiteException ex) {
      showStorageError();
    }
  }

  private void showStorageError() {
    root = column();
    root.setBackgroundColor(BG);
    root.setPadding(dp(24), dp(64), dp(24), dp(24));
    setContentView(root);
    root.addView(text("Дневник сейчас недоступен", 24, INK));
    space(root, 18);
    root.addView(
        text(
            "Не удалось прочитать локальную базу. Данные не сброшены. Проверь свободное место на"
                + " телефоне и перезапусти приложение. Не удаляй приложение и не очищай его данные,"
                + " если нет резервной копии.",
            16,
            MUTED));
    space(root, 22);
    root.addView(button("Повторить", this::recreate));
  }

  private void renderContent() {
    root = column();
    root.setBackgroundColor(BG);
    root.setPadding(dp(22), 0, dp(22), 0);
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          root.setPadding(
              dp(22),
              i.getSystemWindowInsetTop() + dp(14),
              dp(22),
              i.getSystemWindowInsetBottom() + dp(12));
          return i;
        });
    TextView eyebrow = text("МЕСТО ДЛЯ ЛЮБОГО СОСТОЯНИЯ", 10, MUTED);
    eyebrow.setLetterSpacing(.12f);
    root.addView(eyebrow);
    space(root, 8);
    LinearLayout titleRow = row();
    TextView title = text("Девять чувств", 32, INK);
    title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
    titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    titleRow.addView(button("⚙", this::editSet));
    root.addView(titleRow);
    space(root, 16);
    LinearLayout tabs = row();
    TextView history =
        button(
            "История",
            () -> {
              tab = "history";
              render();
            });
    TextView stats =
        button(
            "Статистика",
            () -> {
              tab = "stats";
              render();
            });
    if (!tab.equals("set"))
      (tab.equals("history") ? history : stats).setBackground(bg(0xFF394657, 18));
    weighted(tabs, history);
    weighted(tabs, stats);
    root.addView(tabs);
    space(root, 12);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    content = column();
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (tab.equals("history")) history();
    else if (tab.equals("set")) emotionSet();
    else statistics();
    space(root, 8);
    LinearLayout footer = row();
    weighted(footer, button("+ Отметить", this::chooseEmotion));
    weighted(footer, button("Виджеты", this::widgetPicker));
    root.addView(footer);
    root.requestApplyInsets();
  }

  private void history() {
    long total = store.count();
    if (historyPage * 50 >= total) historyPage = Math.max(0, (int) ((total - 1) / 50));
    List<MoodStore.Entry> entries = store.page(historyPage * 50, 50);
    if (entries.isEmpty()) {
      space(content, 32);
      TextView symbol = text("○", 72, ACCENT);
      content.addView(symbol);
      TextView title = text("Как ты сейчас?", 26, INK);
      title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
      content.addView(title);
      space(content, 12);
      content.addView(
          text(
              "Добавь виджет на рабочий стол.\n"
                  + "Один тап — и чувство сохранено.\n"
                  + "Комментарий можно оставить позже.",
              16,
              MUTED));
      space(content, 24);
      content.addView(button("Сделать первую отметку", this::chooseEmotion));
      return;
    }
    LocalDate previous = null;
    for (MoodStore.Entry e : entries) {
      ZonedDateTime dt = Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault());
      LocalDate day = dt.toLocalDate();
      if (!day.equals(previous)) {
        space(content, 18);
        String label =
            day.equals(LocalDate.now())
                ? "Сегодня"
                : day.equals(LocalDate.now().minusDays(1))
                    ? "Вчера"
                    : day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru")));
        TextView heading = text(label, 14, MUTED);
        content.addView(heading);
        space(content, 8);
        previous = day;
      }
      LinearLayout card = row();
      card.setPadding(dp(14), dp(14), dp(14), dp(14));
      card.setBackground(bg(0xFF1D2229, 20));
      TextView icon = text(e.emotion.symbol, 30, e.emotion.color);
      icon.setGravity(Gravity.CENTER);
      icon.setBackground(bg(emotionSurface(e.emotion.color), 15));
      card.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
      LinearLayout details = column();
      details.setPadding(dp(14), 0, dp(6), 0);
      TextView name = text(e.emotion.title, 17, INK);
      details.addView(name);
      space(details, 4);
      TextView comment = text(e.comment.isEmpty() ? "Добавить комментарий" : e.comment, 13, MUTED);
      comment.setMaxLines(3);
      details.addView(comment);
      card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
      card.addView(text(dt.format(DateTimeFormatter.ofPattern("HH:mm")), 12, MUTED));
      card.setContentDescription(
          e.emotion.title
              + ", "
              + dt.format(DateTimeFormatter.ofPattern("HH:mm"))
              + ", "
              + (e.comment.isEmpty() ? "Добавить комментарий" : e.comment));
      card.setOnClickListener(v -> edit(e));
      card.setFocusable(true);
      content.addView(card, new LinearLayout.LayoutParams(-1, -2));
      space(content, 8);
    }
    if (total > 50) {
      space(content, 12);
      LinearLayout pages = row();
      if (historyPage > 0)
        weighted(
            pages,
            button(
                "← Новее",
                () -> {
                  historyPage--;
                  render();
                }));
      if ((historyPage + 1) * 50 < total)
        weighted(
            pages,
            button(
                "Старее →",
                () -> {
                  historyPage++;
                  render();
                }));
      content.addView(pages);
      space(content, 8);
      content.addView(
          text("Страница " + (historyPage + 1) + " · всего " + total + " отметок", 12, MUTED));
    }
    space(content, 20);
  }

  private void chooseEmotion() {
    List<Emotion> choices = store.slots();
    String[] labels = choices.stream().map(e -> e.symbol + "  " + e.title).toArray(String[]::new);
    new AlertDialog.Builder(this)
        .setTitle("Как ты сейчас?")
        .setItems(
            labels,
            (d, n) -> {
              try {
                store.add(choices.get(n));
                MoodWidget.refreshAll(this);
                tab = "history";
                historyPage = 0;
                render();
                Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
              } catch (Exception ex) {
                error();
              }
            })
        .setNegativeButton("Отмена", null)
        .show();
  }

  private void edit(MoodStore.Entry entry) {
    LinearLayout form = column();
    form.setPadding(dp(22), dp(8), dp(22), 0);
    List<Emotion> choices = new ArrayList<>(store.slots());
    int selection = -1;
    for (int i = 0; i < choices.size(); i++)
      if (choices.get(i).key.equals(entry.emotion.key)) selection = i;
    if (selection < 0) {
      choices.add(0, entry.emotion);
      selection = 0;
    }
    Spinner emotions = new Spinner(this);
    String[] labels = choices.stream().map(e -> e.symbol + "  " + e.title).toArray(String[]::new);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
    emotions.setAdapter(adapter);
    emotions.setSelection(selection);
    form.addView(emotions);
    EditText comment = new EditText(this);
    comment.setText(entry.comment);
    comment.setHint("Что происходило?");
    comment.setMinLines(1);
    comment.setMaxLines(8);
    comment.setGravity(Gravity.TOP);
    comment.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    form.addView(comment);
    comment.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(10000)});
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Отметка")
            .setView(form)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton(
                "Удалить",
                (d, w) ->
                    new AlertDialog.Builder(this)
                        .setTitle("Удалить отметку?")
                        .setMessage("Эту запись нельзя будет восстановить.")
                        .setNegativeButton("Оставить", null)
                        .setPositiveButton(
                            "Удалить",
                            (dd, ww) -> {
                              store.delete(entry.id);
                              MoodWidget.refreshAll(this);
                              render();
                            })
                        .show())
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        store.update(
                            entry.id,
                            choices.get(emotions.getSelectedItemPosition()),
                            comment.getText().toString().trim());
                        MoodWidget.refreshAll(this);
                        dialog.dismiss();
                        render();
                      } catch (Exception ex) {
                        error();
                      }
                    }));
    editor = dialog;
    editorKind = "entry";
    editorEntry = entry.id;
    editorText = comment;
    editorSymbol = null;
    editorSpinner = emotions;
    dialog.show();
  }

  private void error() {
    Toast.makeText(this, "Не удалось сохранить. Попробуй ещё раз.", Toast.LENGTH_LONG).show();
  }

  private void statistics() {
    LinearLayout periods = row();
    String[] labels = {"7 дней", "30 дней", "Всё"};
    int[] values = {7, 30, 0};
    for (int n = 0; n < 3; n++) {
      final int value = values[n];
      TextView chip =
          button(
              labels[n],
              () -> {
                days = value;
                render();
              });
      if (days == value) chip.setBackground(bg(0xFF394657, 18));
      weighted(periods, chip);
    }
    content.addView(periods);
    space(content, 22);
    long since =
        days == 0
            ? 0
            : LocalDate.now()
                .minusDays(days - 1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    MoodStore.Statistics stats = store.statistics(since);
    content.addView(text(String.valueOf(stats.total), 48, INK));
    content.addView(text("отметок · дней с записями: " + stats.activeDays, 14, MUTED));
    space(content, 24);
    if (stats.total == 0) {
      content.addView(text("Пока нет отметок за этот период.\nНачни с одного нажатия.", 17, MUTED));
      return;
    }
    for (Emotion e : stats.emotions.values()) {
      int count = stats.counts.getOrDefault(e.key, 0);
      LinearLayout label = row();
      TextView name =
          text(
              e.symbol + "  " + e.title + (stats.current.contains(e.key) ? "" : " · прежний набор"),
              16,
              INK);
      label.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
      label.addView(text(count + " · " + Math.round(count * 100f / stats.total) + "%", 13, MUTED));
      content.addView(label);
      space(content, 6);
      ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
      bar.setMax(stats.total);
      bar.setProgress(count);
      bar.setProgressTintList(android.content.res.ColorStateList.valueOf(e.color));
      bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF303946));
      content.addView(bar, new LinearLayout.LayoutParams(-1, dp(8)));
      space(content, 18);
    }
    content.addView(
        text(
            "Доли от всех отметок за период.\nПрежние эмоции сохраняются после изменения набора.",
            12,
            MUTED));
    space(content, 20);
  }

  private void editSet() {
    tab = "set";
    render();
  }

  private void emotionSet() {
    space(content, 10);
    content.addView(text("Твои девять кнопок", 24, INK));
    space(content, 10);
    content.addView(
        text(
            "Нажми на ячейку, чтобы заменить эмоцию. Старые отметки и статистика останутся с"
                + " прежними названиями.",
            14,
            MUTED));
    space(content, 18);
    List<Emotion> slots = store.slots();
    for (int r = 0; r < 3; r++) {
      LinearLayout line = row();
      for (int col = 0; col < 3; col++) {
        int position = r * 3 + col;
        Emotion e = slots.get(position);
        TextView cell = button(e.symbol + "\n" + e.title, () -> editSlot(position));
        cell.setBackground(bg(emotionSurface(e.color), 18));
        cell.setTextSize(12);
        cell.setMinHeight(dp(86));
        cell.setContentDescription("Ячейка " + (position + 1) + ": " + e.title + ". Изменить");
        weighted(line, cell);
      }
      content.addView(line);
      space(content, 8);
    }
    space(content, 14);
    content.addView(
        text("Центральная ячейка тоже редактируется.\nСетка всегда остаётся 3 × 3.", 13, MUTED));
    space(content, 22);
    content.addView(button("Резервная копия", this::backupMenu));
    space(content, 8);
    content.addView(button("О приложении и конфиденциальность", this::about));
    space(content, 20);
  }

  private void editSlot(int position) {
    Emotion current = store.slots().get(position);
    LinearLayout form = column();
    form.setPadding(dp(22), dp(8), dp(22), 0);
    form.addView(text("Название", 13, MUTED));
    EditText name = new EditText(this);
    name.setSingleLine(true);
    name.setText(current.title);
    name.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(24)});
    form.addView(name);
    form.addView(text("Значок или эмодзи", 13, MUTED));
    EditText symbol = new EditText(this);
    symbol.setSingleLine(true);
    symbol.setText(current.symbol);
    symbol.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(12)});
    form.addView(symbol);
    space(form, 8);
    form.addView(text("Цвет", 13, MUTED));
    Spinner color = new Spinner(this);
    String[] colorNames = {
      "Жёлтый",
      "Песочный",
      "Зелёный",
      "Коралловый",
      "Нейтральный",
      "Голубой",
      "Сиреневый",
      "Оливковый",
      "Персиковый"
    };
    color.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, colorNames));
    for (int i = 0; i < Emotion.COLORS.length; i++)
      if (current.color == Emotion.COLORS[i]) color.setSelection(i);
    form.addView(color);
    space(form, 8);
    form.addView(
        text("Изменится только кнопка. Прошлые записи сохранят прежнее значение.", 12, MUTED));
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Ячейка " + (position + 1))
            .setView(form)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String title = name.getText().toString().trim(),
                          icon = symbol.getText().toString().trim();
                      if (title.isEmpty()) {
                        name.setError("Введи название");
                        return;
                      }
                      if (icon.isEmpty()) {
                        symbol.setError("Введи значок");
                        return;
                      }
                      try {
                        store.replaceSlot(
                            position, title, icon, Emotion.COLORS[color.getSelectedItemPosition()]);
                        MoodWidget.refreshAll(this);
                        dialog.dismiss();
                        render();
                      } catch (Exception ex) {
                        error();
                      }
                    }));
    editor = dialog;
    editorKind = "slot";
    editorSlot = position;
    editorText = name;
    editorSymbol = symbol;
    editorSpinner = color;
    dialog.show();
  }

  private void widgetPicker() {
    new AlertDialog.Builder(this)
        .setTitle("Виджет на рабочий стол")
        .setItems(
            new String[] {"Компактный · 2×2", "С подписями · 3×3"},
            (d, n) -> {
              AppWidgetManager m = AppWidgetManager.getInstance(this);
              if (android.os.Build.VERSION.SDK_INT >= 26 && m.isRequestPinAppWidgetSupported())
                m.requestPinAppWidget(
                    new ComponentName(this, n == 0 ? MoodWidget.class : LargeMoodWidget.class),
                    null,
                    null);
              else
                new AlertDialog.Builder(this)
                    .setMessage("Зажми пустое место на рабочем столе → Виджеты → Девять чувств.")
                    .setPositiveButton("Понятно", null)
                    .show();
            })
        .setNegativeButton("Закрыть", null)
        .show();
  }

  private void backupMenu() {
    new AlertDialog.Builder(this)
        .setTitle("Резервная копия")
        .setMessage(
            "Копия содержит записи, комментарии и набор эмоций в читаемом JSON-файле. Храни её в"
                + " подходящем тебе месте. Приложение само ничего не отправляет.")
        .setPositiveButton(
            "Сохранить копию",
            (d, w) ->
                startActivityForResult(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_TITLE, "nine-feelings-" + LocalDate.now() + ".json"),
                    101))
        .setNeutralButton(
            "Восстановить",
            (d, w) ->
                new AlertDialog.Builder(this)
                    .setTitle("Восстановить из файла?")
                    .setMessage(
                        "Добавятся отсутствующие записи без дубликатов. Существующие записи и"
                            + " комментарии не изменятся. Набор кнопок заменится набором из копии.")
                    .setPositiveButton(
                        "Выбрать файл",
                        (dd, ww) ->
                            startActivityForResult(
                                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("application/json"),
                                102))
                    .setNegativeButton("Отмена", null)
                    .show())
        .setNegativeButton("Закрыть", null)
        .show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK
        || data == null
        || data.getData() == null
        || (request != 101 && request != 102)) return;
    android.net.Uri uri = data.getData();
    Context app = getApplicationContext();
    Toast.makeText(this, "Обрабатываю копию…", Toast.LENGTH_SHORT).show();
    new Thread(
            () -> {
              String message;
              try (MoodStore background = new MoodStore(app)) {
                if (request == 101) {
                  try (java.io.OutputStream output =
                      app.getContentResolver().openOutputStream(uri, "w")) {
                    if (output == null) throw new java.io.IOException();
                    BackupCodec.write(background, output);
                  }
                  message = "Резервная копия сохранена";
                } else {
                  org.json.JSONObject backup;
                  try (java.io.InputStream input = app.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new java.io.IOException();
                    backup = BackupCodec.read(input);
                  }
                  int added = BackupCodec.restore(background, backup);
                  message = "Восстановлено новых записей: " + added;
                  MoodWidget.refreshAll(app);
                }
              } catch (Exception ex) {
                message =
                    "Не удалось обработать копию. Проверь формат файла и доступ к нему. Записи не"
                        + " заменены.";
              }
              final String outcome = message;
              runOnUiThread(
                  () -> {
                    if (!isDestroyed() && !isFinishing()) {
                      historyPage = 0;
                      render();
                      Toast.makeText(this, outcome, Toast.LENGTH_LONG).show();
                    }
                  });
            },
            "mood-backup")
        .start();
  }

  private void about() {
    new AlertDialog.Builder(this)
        .setTitle("Девять чувств · 0.4.1")
        .setMessage(
            "Личный дневник эмоций.\n\n"
                + "Данные и настройки хранятся только внутри приложения. Нет рекламы, аналитики,"
                + " аккаунтов и сетевых разрешений. Удаление приложения удаляет локальный дневник;"
                + " для переноса используй резервную копию. Копия — читаемый файл, место хранения"
                + " выбираешь ты.\n\n"
                + "Приложение не ставит диагнозы и не заменяет медицинскую помощь.\n\n"
                + "Открытое программное обеспечение · MIT License. Авторские права: Nine Feelings"
                + " contributors, 2026.")
        .setPositiveButton("Закрыть", null)
        .setNeutralButton("Лицензия MIT", (d, w) -> showLicense())
        .show();
  }

  private void showLicense() {
    try (java.io.InputStream input = getAssets().open("LICENSE")) {
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      byte[] chunk = new byte[4096];
      int n;
      while ((n = input.read(chunk)) != -1) bytes.write(chunk, 0, n);
      TextView license = text(bytes.toString("UTF-8"), 13, INK);
      license.setPadding(dp(22), dp(12), dp(22), dp(12));
      ScrollView scroll = new ScrollView(this);
      scroll.addView(license);
      new AlertDialog.Builder(this)
          .setTitle("MIT License")
          .setView(scroll)
          .setPositiveButton("Закрыть", null)
          .show();
    } catch (java.io.IOException ex) {
      Toast.makeText(this, "Лицензия находится в исходном коде приложения", Toast.LENGTH_LONG)
          .show();
    }
  }
}
