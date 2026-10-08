# art

`logo.png` 是应用图标（已去掉原图那圈白色描边，中心图形未改动）。

改图标后重新生成各密度：

```bash
python3 -c "
from PIL import Image; import os
src = Image.open('art/logo.png').convert('RGB')
for d, px in {'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}.items():
    out = f'app/src/main/res/mipmap-{d}'
    os.makedirs(out, exist_ok=True)
    src.resize((px, px), Image.LANCZOS).save(f'{out}/ic_launcher.png', 'PNG', optimize=True)
"
```

清单里由 `android:icon="@mipmap/ic_launcher"` 引用。
