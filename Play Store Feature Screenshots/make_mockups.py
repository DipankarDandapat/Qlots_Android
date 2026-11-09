"""Builds the Google Play feature screenshots: real app screens inside phone mockups with marketing headlines.

    pip install pillow
    python "Play Store Feature Screenshots/make_mockups.py"

Inputs : source/NN-name.png   (raw 1080x2400 emulator screenshots of the app, demo data)
Outputs: NN-name.png          (1080x1920, within Play Console's phone screenshot limits)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
SRC = HERE / "source"
OUT = HERE
W, H = 1080, 1920

FONT_FILE = "/System/Library/Fonts/Avenir Next.ttc"


def font(size: int, weight: str = "bold") -> ImageFont.FreeTypeFont:
    for path, idx in ((FONT_FILE, {"bold": 0, "heavy": 8, "medium": 5, "regular": 7}.get(weight, 0)), ("/System/Library/Fonts/HelveticaNeue.ttc", 1 if weight == "bold" else 0)):
        try:
            return ImageFont.truetype(path, size, index=idx)
        except Exception:
            continue
    return ImageFont.load_default()


SLIDES = [
    ("01-overview", "YOUR MONEY, ONE VIEW", ["Your whole financial", "picture at a glance"], "Net worth, assets, debts and monthly surplus — refreshed every night at 12 AM.", ((18, 74, 55), (10, 38, 29))),
    ("02-networth", "NET-WORTH TIMELINE", ["Watch your net worth", "grow, month by month"], "Timeline and assets-vs-liabilities graphs built from your own snapshots.", ((22, 96, 70), (12, 52, 38))),
    ("03-projection", "PROJECTION VS ACTUAL", ["See how your plan is", "really tracking"], "Compare saved projections with what actually happened and refine your assumptions.", ((38, 84, 98), (17, 44, 56))),
    ("04-calculation", "TRANSPARENT MATHS", ["Every projection,", "fully explained"], "End dates, each asset's calculation and today's-money values for 1, 2, 3 and 5 years.", ((18, 74, 55), (9, 34, 26))),
    ("05-cashflow", "INCOME VS EXPENSES", ["Spot seasonal spending", "before it bites"], "Monthly income, expenses and surplus — with annual and quarterly bills highlighted.", ((70, 98, 52), (31, 54, 28))),
    ("06-allocation", "ASSET ALLOCATION", ["Know exactly where", "your money sits"], "Cash, deposits, investments and property — shown as one clear chart.", ((24, 86, 80), (12, 46, 44))),
    ("07-goals", "GOALS THAT STICK", ["Reach every goal", "on time"], "Emergency fund, home deposit, education — with the monthly amount you need.", ((92, 80, 40), (50, 42, 20))),
    ("08-outlook", "PLAN AHEAD", ["See 2, 3 and 5 years", "ahead — your way"], "Conservative, base and optimistic paths. Adjust savings, growth and loan repayment.", ((18, 74, 55), (10, 38, 29))),
]


def gradient(top, bottom) -> Image.Image:
    img = Image.new("RGB", (W, H), top)
    px = img.load()
    for y in range(H):
        t = y / (H - 1)
        row = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(W):
            px[x, y] = row
    return img


def rounded_mask(size, radius) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def wrap(draw, text, fnt, max_width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        trial = f"{cur} {w}".strip()
        if draw.textlength(trial, font=fnt) <= max_width:
            cur = trial
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def build(name, eyebrow, headline, sub, colors):
    bg = gradient(*colors).convert("RGBA")
    deco = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(deco)
    d.ellipse((-260, -300, 520, 480), fill=(255, 255, 255, 14))
    d.ellipse((620, 1180, 1500, 2060), fill=(255, 255, 255, 12))
    d.ellipse((760, 120, 1180, 540), fill=(200, 232, 169, 18))
    bg = Image.alpha_composite(bg, deco)
    draw = ImageDraw.Draw(bg)

    margin = 72
    # eyebrow pill
    f_eyebrow = font(26, "bold")
    tw = draw.textlength(eyebrow, font=f_eyebrow)
    draw.rounded_rectangle((margin, 86, margin + tw + 44, 86 + 52), radius=26, fill=(200, 232, 169, 255))
    draw.text((margin + 22, 98), eyebrow, font=f_eyebrow, fill=(18, 61, 46))
    # headline
    f_head = font(82, "heavy")
    y = 170
    for line in headline:
        draw.text((margin, y), line, font=f_head, fill=(255, 255, 255))
        y += 96
    # sub-headline
    f_sub = font(33, "medium")
    y += 14
    for line in wrap(draw, sub, f_sub, W - 2 * margin - 30):
        draw.text((margin, y), line, font=f_sub, fill=(205, 226, 214))
        y += 46

    # phone
    shot = Image.open(SRC / f"{name}.png").convert("RGB")
    screen_w = 736
    screen_h = int(shot.height * screen_w / shot.width)
    shot = shot.resize((screen_w, screen_h), Image.LANCZOS)
    bezel = 20
    phone_w, phone_h = screen_w + 2 * bezel, screen_h + 2 * bezel
    px0 = (W - phone_w) // 2
    py0 = 520
    frame = Image.new("RGBA", (phone_w, phone_h), (0, 0, 0, 0))
    fd = ImageDraw.Draw(frame)
    fd.rounded_rectangle((0, 0, phone_w - 1, phone_h - 1), radius=92, fill=(16, 18, 17, 255))
    fd.rounded_rectangle((3, 3, phone_w - 4, phone_h - 4), radius=89, outline=(70, 76, 73, 255), width=3)
    frame.paste(shot, (bezel, bezel), rounded_mask(shot.size, 72))
    # punch-hole camera
    fd = ImageDraw.Draw(frame)
    fd.ellipse((phone_w // 2 - 15, bezel + 20, phone_w // 2 + 15, bezel + 50), fill=(8, 9, 9, 255))
    # soft shadow
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((px0 + 10, py0 + 26, px0 + phone_w - 10, py0 + phone_h + 26), radius=92, fill=(0, 0, 0, 120))
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    bg = Image.alpha_composite(bg, shadow)
    bg.alpha_composite(frame, (px0, py0))
    out = bg.convert("RGB")
    assert out.size == (W, H)
    out.save(OUT / f"{name}.png", optimize=True)
    print("wrote", OUT / f"{name}.png")


if __name__ == "__main__":
    for slide in SLIDES:
        build(*slide)
