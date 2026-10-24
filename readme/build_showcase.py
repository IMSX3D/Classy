"""Build README artwork from explicitly selected, real-device demo screenshots.

Usage: python readme/build_showcase.py CAPTURE_DIRECTORY
Never glob the input directory: it may contain private diagnostic screenshots.
Only outer system bars are cropped; the application UI is never repainted.
Requires Pillow, numpy and opencv-python. Original captures remain outside the repository.
"""
from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageFont

import sys, os

_FONT_CANDIDATES = {
    "win32": [r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\msyhbd.ttc", r"C:\Windows\Fonts\simhei.ttf"],
    "darwin": ["/System/Library/Fonts/PingFang.ttc", "/System/Library/Fonts/STHeiti Medium.ttc"],
    "linux": ["/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
              "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"],
}


def default_cjk_font():
    """按平台挑一个可用的中文字体；找不到时给出明确提示。"""
    for path in _FONT_CANDIDATES.get(sys.platform, []):
        if os.path.exists(path):
            return path
    raise FileNotFoundError(
        "找不到中文字体：请用 --font 指定 .ttc/.ttf，或先安装 Noto Sans CJK / 微软雅黑。"
    )

source = Path(sys.argv[1])
output = Path(__file__).parent / 'media' / 'v0.1.1'
output.mkdir(parents=True, exist_ok=True)
screens = {
    'phone-fill': 'phone-fill', 'phone-bar': 'phone-bar',
    'phone-dark-fill': 'phone-dark-fill', 'phone-dark-bar': 'phone-dark-bar',
    'phone-today': 'phone-today', 'phone-week': 'phone-week',
    'phone-semester': 'phone-semester',
    'phone-background-light': 'phone-background-light',
    'phone-background-dark': 'phone-background-dark',
    'phone-guide': 'phone-guide', 'phone-guide-lesson': 'phone-guide-lesson',
}
for name, file in screens.items():
    im=Image.open(source/(file+'.png')).convert('RGB')
    assert im.size==(1080,2400), (file,im.size)
    im.crop((0,114,1080,2360)).save(output/(name+'.png'), optimize=True)
for name in ['widget-small','widget-today','widget-two-day','widget-week']:
    Image.open(source/(name+'.png')).save(output/(name+'.png'),optimize=True)

# Simple editorial sheets keep captions outside the actual screenshots.
font_path=default_cjk_font()
def font(size): return ImageFont.truetype(font_path,size)
def sheet(name, title, subtitle, items, dark=False):
    bg='#101b23' if dark else '#f1f4f1'
    fg='#f0f5ee' if dark else '#172c2b'
    width=1600; margin=64; gap=40
    cell=(width-2*margin-gap*(len(items)-1))//len(items)
    thumbs=[]
    for filename,label in items:
        im=Image.open(output/(filename+'.png')).convert('RGB')
        im.thumbnail((cell,1060),Image.Resampling.LANCZOS)
        thumbs.append((im,label))
    height=280+max(im.height for im,_ in thumbs)+80
    canvas=Image.new('RGB',(width,height),bg);d=ImageDraw.Draw(canvas)
    d.text((margin,42),title,font=font(50),fill=fg)
    d.text((margin,118),subtitle,font=font(27),fill=fg)
    for i,(im,label) in enumerate(thumbs):
        x=margin+i*(cell+gap)+(cell-im.width)//2
        d.text((margin+i*(cell+gap),200),label,font=font(29),fill=fg)
        canvas.paste(im,(x,260))
    canvas.save(output/(name+'.jpg'),quality=92,optimize=True)

sheet('week-and-today','从整周，到今天。','看清课程、时间与地点。',
 [('phone-fill','整周课表'),('phone-week','按天浏览'),('phone-today','专注今天')])
sheet('appearance','你的课表，你的颜色。','色条与填充，浅色与深色。',
 [('phone-bar','浅色 · 色条'),('phone-dark-bar','深色 · 色条'),('phone-dark-fill','深色 · 填充')])
sheet('background','换一张背景，也换一种心情。','真实自定义背景效果 · 原创渐变示例',
 [('phone-background-light','晨光'),('phone-background-dark','极光')],True)
sheet('guide','第一次用，也能轻松上手。','边试边学，随时回来重看。',
 [('phone-guide','八个主题'),('phone-guide-lesson','互动练习')])

canvas=Image.new('RGB',(1600,1460),'#edf1ee');d=ImageDraw.Draw(canvas)
d.text((64,46),'不用打开 App，也能心中有数。',font=font(50),fill='#172c2b')
d.text((64,125),'四种桌面小组件 · vivo 手机实际桌面截图',font=font(27),fill='#425b56')
for name,label,x,y,w in [('widget-small','今日·小',214,245,400),
 ('widget-today','今日',64,865,700),('widget-two-day','两日',840,245,696),
 ('widget-week','本周',840,675,696)]:
    im=Image.open(output/(name+'.png')).convert('RGB')
    d.text((x,y-50),label,font=font(29),fill='#172c2b')
    canvas.paste(im.resize((w,round(im.height*w/im.width)),Image.Resampling.LANCZOS),(x,y))
canvas.save(output/'widgets.jpg',quality=92,optimize=True)

tablet=source/'tablet'
if tablet.exists():
    for name in ['tablet-grid-landscape','tablet-dark-landscape','tablet-dark-portrait',
                 'tablet-semester-portrait','tablet-calendar-portrait']:
        im=Image.open(tablet/(name+'.png')).convert('RGB')
        assert im.size in [(2800,1968),(1968,2800)], (name,im.size)
        im.crop((0,95,im.width,im.height-35)).save(output/(name+'.png'),optimize=True)
    home=Image.open(tablet/'tablet-home.png')
    for name,box in [('tablet-widget-small',(333,214,875,630)),
                     ('tablet-widget-today',(1129,214,2467,630)),
                     ('tablet-widget-two-day',(333,758,1671,1174))]:
        home.crop(box).save(output/(name+'.png'),optimize=True)
    Image.open(tablet/'tablet-widget-expanded.png').crop((334,214,1671,1446)).save(output/'tablet-widget-week.png',optimize=True)
    sheet('tablet-portrait','大屏，也有舒适的阅读节奏。','vivo 平板竖屏实拍',
          [('tablet-semester-portrait','学期安排'),('tablet-calendar-portrait','日期详情')])
    # Use verified clean captures; the retired phone calendar contains an alarm overlay.
    sheet('semester','把整个学期，看得更清楚。','从课程周次分布，走到具体上课日期。 · 平板实拍',
          [('tablet-semester-portrait','课程总览'),('tablet-calendar-portrait','上课日历')])
    hero=Image.new('RGB',(1600,1050),'#e9efeb');d=ImageDraw.Draw(hero)
    d.text((64,38),'MAKE TIME YOURS.',font=font(60),fill='#173c35')
    d.text((64,132),'手机、平板，一份从容的课程安排。',font=font(30),fill='#425b56')
    for name,x,w in [('phone-bar',64,350),('tablet-grid-landscape',470,1066)]:
        im=Image.open(output/(name+'.png')).convert('RGB')
        hero.paste(im.resize((w,round(im.height*w/im.width)),Image.Resampling.LANCZOS),(x,240))
    hero.save(output/'hero.jpg',quality=93,optimize=True)
    canvas=Image.new('RGB',(1600,1420),'#edf1ee');d=ImageDraw.Draw(canvas)
    d.text((64,46),'把课程，放在平板桌面。',font=font(50),fill='#172c2b')
    d.text((64,125),'真实桌面实拍 · 可按桌面允许的网格调整大小',font=font(27),fill='#425b56')
    for name,label,x,y,w in [('tablet-widget-small','今日·小',64,245,420),
       ('tablet-widget-today','今日',64,720,700),('tablet-widget-two-day','两日',64,1060,700),
       ('tablet-widget-week','本周 · 调整高度后',840,245,696)]:
        im=Image.open(output/(name+'.png')).convert('RGB')
        d.text((x,y-50),label,font=font(29),fill='#172c2b')
        canvas.paste(im.resize((w,round(im.height*w/im.width)),Image.Resampling.LANCZOS),(x,y))
    canvas.save(output/'tablet-widgets.jpg',quality=92,optimize=True)

# Apply the same contour cleanup after any fresh capture rebuild.
from build_widget_showcase import main as build_widget_showcase
build_widget_showcase()
