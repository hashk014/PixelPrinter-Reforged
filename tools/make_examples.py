from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

folder = Path("src/main/resources/images")
folder.mkdir(exist_ok=True)
font = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 13)
frames = []
for accent in ["#fa493c", "#ffb229", "#45b6a3"]:
    image = Image.new("RGB", (96, 32), "#111820")
    draw = ImageDraw.Draw(image)
    for y in range(4, 28, 4):
        for x in range(4, 28, 4):
            if (x // 4 + y // 4) % 3 != 0:
                draw.rectangle((x, y, x + 2, y + 2), fill=accent)
    draw.text((34, 2), "HASH014", font=font, fill="#f4efe5")
    draw.text(
        (34, 17),
        "REFORGED",
        font=ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 10),
        fill=accent,
    )
    frames.append(image)
frames[0].save(folder / "hash014.png")
frames[0].save(
    folder / "hash014.gif",
    save_all=True,
    append_images=frames[1:],
    duration=[300, 300, 400],
    loop=0,
    disposal=2,
)
