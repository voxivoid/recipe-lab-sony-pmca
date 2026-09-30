Recipe Lab bundled CJK fonts
============================

Cut-down derivatives of Noto Sans CJK, holding only the glyphs the app's Chinese text uses
(src/com/voxivoid/recipelab/TextZhHans.java and TextZhHant.java), plus printable ASCII and
the English table's characters. Renamed as the SIL Open Font License asks of a modified font.

Upstream:  https://github.com/notofonts/noto-cjk
Commit:    f8d157532fbfaeda587e826d4cd5b21a49186f7c
License:   SIL Open Font License 1.1 (OFL.txt, the upstream Sans/LICENSE)

Sources (not committed, about 16 MB each):
  Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf
    SHA-256 d68bafcb48a2707749396aa12bbbd833cb70401f3a9a689fd2902c7e0d295964
  Sans/Variable/TTF/Subset/NotoSansTC-VF.ttf
    SHA-256 ac091cc8cd19e848202afc8fe6d3809b4526c8fdbdb4be82da20c4f785949591

Generated (Regular weight instanced, static TrueType for Android 2.3):
  RecipeLabCJKsc-Regular.ttf   family "Recipe Lab CJK SC"
  RecipeLabCJKtc-Regular.ttf   family "Recipe Lab CJK TC"

Rebuild after any change to the Chinese text:

    python3 tools/subset-font.py

It prints each output's size and SHA-256. The files are not byte-for-byte reproducible
across fonttools versions; LangTest checks what matters, that each font maps every
character its table uses.
