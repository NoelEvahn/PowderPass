# Contact sheet: python3 sheet.py out.png cols img1 img2 ...
import sys
from PIL import Image, ImageDraw
out, cols, files = sys.argv[1], int(sys.argv[2]), sys.argv[3:]
ims = [Image.open(f).convert('RGB') for f in files]
w, h = ims[0].size
tw, th = w // 2, h // 2
rows = (len(ims) + cols - 1) // cols
sheet = Image.new('RGB', (tw * cols, th * rows), 'black')
for i, im in enumerate(ims):
    im = im.resize((tw, th))
    d = ImageDraw.Draw(im); d.text((6, 4), files[i].split('/')[-1], fill=(255, 255, 0))
    sheet.paste(im, ((i % cols) * tw, (i // cols) * th))
sheet.save(out)
