const fs=require('node:fs'),path=require('node:path');
const base=path.join(__dirname,'app/src/main/res');
const colors=['#F0D88A','#E3C49E','#C5D6B7','#E9AAA0','#DDD9D1','#B8CCDC','#D1C0DF','#BFCBA8','#F0BB98'];
const darkColors=colors.map(c=>{const rgb=[1,3,5].map((pos,i)=>Math.round(parseInt(c.slice(pos,pos+2),16)*.22+[29,34,41][i]*.78));return '#'+rgb.map(n=>n.toString(16).padStart(2,'0')).join('');});
const names=['Радость','Интерес','Спокойствие','Злость','Нейтрально','Грусть','Тревога','Отвращение','Воодушевление'];
const symbols=['☺','✧','≈','ϟ','○','☂','〰','×','✺'];
function write(p,s){const f=path.join(base,p);fs.mkdirSync(path.dirname(f),{recursive:true});fs.writeFileSync(f,s);}
write('drawable/widget_bg.xml','<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="#12151A"/><corners android:radius="24dp"/></shape>');
darkColors.forEach((c,i)=>write(`drawable/cell_${i}.xml`,`<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="#33FFFFFF"><item><shape><solid android:color="${c}"/><corners android:radius="14dp"/></shape></item></ripple>`));
for(const compact of [true,false]){
 let xml='<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:background="@drawable/widget_bg"><LinearLayout android:id="@+id/widget_grid" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:padding="8dp">';
 xml+='<TextView android:id="@+id/widget_header" android:layout_width="match_parent" android:layout_height="32dp" android:gravity="center_vertical" android:paddingStart="6dp" android:text="Девять чувств  ↗" android:contentDescription="Открыть историю эмоций" android:textSize="13sp" android:textStyle="bold" android:textColor="#EEF2F6"/>';
 for(let r=0;r<3;r++){
 xml+='<LinearLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:orientation="horizontal">';
 for(let col=0;col<3;col++){
 const i=r*3+col;
 xml+=`<TextView android:id="@+id/cell${i}" android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1" android:layout_margin="2dp" android:gravity="center" android:padding="2dp" android:background="@drawable/cell_${i}" android:text="${symbols[i]}${compact?'':'&#10;'+names[i]}" android:contentDescription="${names[i]} — записать эмоцию" android:textSize="${compact?24:10}sp" android:textColor="#EEF2F6" android:maxLines="3" android:fontFamily="sans-serif-medium"/>`;
 }
 xml+='</LinearLayout>';
 }
 xml+='<TextView android:id="@+id/widget_status" android:layout_width="match_parent" android:layout_height="26dp" android:gravity="center_vertical" android:paddingStart="6dp" android:text="Нажми на эмоцию" android:textColor="#A6B0BE" android:textSize="10sp" android:maxLines="1" android:ellipsize="end"/></LinearLayout>';
 xml+='<LinearLayout android:id="@+id/confirmation" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center" android:padding="12dp" android:background="@drawable/widget_bg" android:visibility="gone" android:clickable="true" android:focusable="true"><TextView android:id="@+id/confirm_symbol" android:layout_width="match_parent" android:layout_height="56dp" android:gravity="center" android:textSize="36sp" android:textColor="#EEF2F6"/><TextView android:id="@+id/confirm_title" android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center" android:textSize="16sp" android:textStyle="bold" android:textColor="#EEF2F6" android:maxLines="3"/><TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="10dp" android:gravity="center" android:text="Нажми, чтобы добавить комментарий" android:textSize="11sp" android:textColor="#A6B0BE"/></LinearLayout></FrameLayout>';
 if(compact)xml=xml.replace('android:padding="8dp"','android:padding="6dp"').replace('android:layout_height="32dp"','android:layout_height="20dp"').replace('android:layout_height="26dp"','android:layout_height="14dp"');
 write(`layout/widget_${compact?'compact':'full'}.xml`,xml);
}
for(const [name,cells,w,h] of [['small',2,110,110],['large',3,180,180]])write(`xml/widget_${name}.xml`,`<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android" android:minWidth="${w}dp" android:minHeight="${h}dp" android:targetCellWidth="${cells}" android:targetCellHeight="${cells}" android:minResizeWidth="110dp" android:minResizeHeight="110dp" android:resizeMode="horizontal|vertical" android:updatePeriodMillis="0" android:initialLayout="@layout/widget_${name==='small'?'compact':'full'}" android:previewLayout="@layout/widget_${name==='small'?'compact':'full'}" android:widgetCategory="home_screen"/>`);
