"""Deterministic, data-free picker previews. Run with Pillow and a CJK font installed.
Both previewImage and previewLayout use these assets to avoid launcher-dependent samples.
"""
from pathlib import Path
import argparse
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont
ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--font', default='C:/Windows/Fonts/msyh.ttc')
args = parser.parse_args()
# Preview canvas uses 2.25 pixels per dp (small 160dp; regular 320dp wide).
DIMENS = ET.parse(ROOT/'app/src/main/res/values/widget_spacing.xml').getroot()
CORNER = float(next(n.text for n in DIMENS if n.get('name') == 'widget_corner_radius').removesuffix('dp')) * 2.25
BG = '#F7F8FA'
INK = '#303038'
MUTED = '#697079'
COLORS = ['#D997AC', '#AE9ACC', '#86B8B3', '#92ABCF']
NAMES = {'zh': ['高等数学', '程序设计', '大学英语', '大学物理'],
         'en': ['Calculus', 'Programming', 'English', 'Physics']}
TIMES = ['08:00–09:35', '10:00–11:35']
def font(size): return ImageFont.truetype(args.font, size)
def label(d, xy, value, size=22, fill=INK): d.text(xy, value, font=font(size), fill=fill)
def card(w,h):
 im=Image.new('RGBA',(w,h),(0,0,0,0));d=ImageDraw.Draw(im)
 d.rounded_rectangle((0,0,w-1,h-1),radius=CORNER,fill=BG)
 return im,d
def course(d,x,y,width,index,slot,lang):
 d.rounded_rectangle((x,y+4,x+5,y+85),radius=2,fill=COLORS[index])
 name=NAMES[lang][index]
 # Fit the actual compact card width without truncating the sample title.
 size=26
 while font(size).getlength(name)>width-20: size-=1
 label(d,(x+16,y),name,size)
 label(d,(x+16,y+34),('教室 ' if lang=='zh' else 'Room ')+str(201+index),20,MUTED)
 label(d,(x+16,y+62),TIMES[slot],20,MUTED)
def draw(kind,lang,filled=False):
 w,h=(360,360) if kind=='today_small' else (720,720) if kind=='weekgrid' else (720,360)
 im,d=card(w,h)
 label(d,(24,20),'示例课表' if lang=='zh' else 'Sample schedule',22)
 if kind=='weekgrid':
  label(d,(450,22),'10/12–10/18',20,MUTED)
  left,top,cw,rh=52,100,92,94
  days=['一','二','三','四','五','六','日'] if lang=='zh' else ['Mon','Tue','Wed','Thu','Fri','Sat','Sun']
  for day in range(7):
   x=left+day*cw
   label(d,(x+16,58),('周'+days[day]) if lang=='zh' else days[day],18,MUTED)
   label(d,(x+27,81),str(12+day),16,MUTED)
  for node in range(12): label(d,(15,top+node*rh/2+16),str(node+1),18,MUTED)
  # A realistic spread across morning, afternoon and evening; preserve empty slots.
  for day,row,span,index in [(0,0,2,0),(0,2,2,1),(1,0,2,2),(1,4,3,3),(2,4,3,0),(3,2,2,1),(4,0,2,2),(4,8,3,3)]:
   x,y=left+day*cw,top+row*rh/2+6
   ch=span*rh/2-6
   d.rounded_rectangle((x,y,x+cw-6,y+ch),radius=8,fill=COLORS[index] if filled else '#FFFFFF')
   if not filled: d.rounded_rectangle((x,y+6,x+5,y+ch-6),radius=2,fill=COLORS[index])
   name=NAMES[lang][index]
   chunks=[name[i:i+2] for i in range(0,len(name),2)] if lang=='zh' else (['Program', 'ming'] if index==1 else [name])
   size=19 if lang=='zh' else 15
   line_h=23 if lang=='zh' else 19
   block_h=len(chunks)*line_h
   name_y=y+max(5,(ch-24-block_h)/2)
   for line,t in enumerate(chunks):
    tx=x+5+(cw-16-font(size).getlength(t))/2
    label(d,(tx,name_y+line*line_h),t,size)
   room=str(201+index)
   label(d,(x+5+(cw-16-font(15).getlength(room))/2,y+ch-22),room,15,MUTED)
 elif kind=='twoday':
  label(d,(600,22),'10/12',20,MUTED)
  for col in range(2):
   x=24+col*348
   label(d,(x,58),(['今天','明天'] if lang=='zh' else ['Today','Tomorrow'])[col],20,MUTED)
   course(d,x,100,310,col*2,0,lang);course(d,x,218,310,col*2+1,1,lang)
  d.line((355,66,355,320),fill='#DFE3E8',width=2)
 else:
  label(d,(24,55),('周一 ' if lang=='zh' else 'Mon ' )+'10/12',20,MUTED)
  course(d,24,103,w-48,0,0,lang);course(d,24,220,w-48,1,1,lang)
 return im
for lang in ['zh','en']:
 folder=ROOT/'app/src/main/res'/('drawable-nodpi' if lang=='zh' else 'drawable-en-nodpi')
 folder.mkdir(exist_ok=True)
 for kind in ['today_small','today','twoday','weekgrid']:
  draw(kind,lang).save(folder/f'widget_preview_{kind}.png')
  # Some historical resources exist in both density folders; keep both identical.
  legacy = ROOT/'app/src/main/res/drawable'/f'widget_preview_{kind}.png'
  if lang == 'zh' and legacy.exists(): draw(kind,lang).save(legacy)
# Contact sheet is for development inspection only, not packaged in the app.
if __name__=='__main__':
 sheet=Image.new('RGB',(1540,1160),'#E5E8EC')
 for kind,xy,size in [('today_small',(20,20),(360,360)),('today',(400,20),(720,360)),('twoday',(20,420),(720,360)),('weekgrid',(780,420),(720,720))]:
  im=draw(kind,'zh').resize(size);sheet.paste(im,xy,im)
 sheet.save(ROOT.parent/'99-整理记录/widget-preview-contact.png')

 comparison=Image.new('RGB',(1500,790),'#E5E8EC')
 cd=ImageDraw.Draw(comparison)
 for filled,x,title in [(False,20,'色条模式 · 示意'),(True,760,'填充模式 · 示意')]:
  label(cd,(x,12),title,24)
  preview=draw('weekgrid','zh',filled)
  comparison.paste(preview,(x,55),preview)
 comparison.save(ROOT.parent/'99-整理记录/widget-week-style-comparison.png')
