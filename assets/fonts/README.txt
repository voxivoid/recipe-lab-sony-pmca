Recipe Lab bundled CJK fonts
============================

These are compact, application-specific derivatives of Noto Sans CJK Regular.
They contain only the glyphs required by the bundled Simplified Chinese and
Traditional Chinese translations, plus the ASCII characters used by the UI.

Upstream: https://github.com/notofonts/noto-cjk
Pinned commit: f8d157532fbfaeda587e826d4cd5b21a49186f7c
License: SIL Open Font License 1.1 (see OFL.txt)

Source files:
- Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf
  SHA-256: D68BAFCB48A2707749396AA12BBBD833CB70401F3A9A689FD2902C7E0D295964
- Sans/Variable/TTF/Subset/NotoSansTC-VF.ttf
  SHA-256: AC091CC8CD19E848202AFC8FE6D3809B4526C8FDBDB4BE82DA20C4F785949591

Generated files:
- RecipeLabCJKsc-Regular.ttf
  Family: Recipe Lab CJK SC
  SHA-256: C66C760E65A44C80A5A3C1AA64A96750E19F2107715882002BF2F16A004B769A
- RecipeLabCJKtc-Regular.ttf
  Family: Recipe Lab CJK TC
  SHA-256: FDA138AAB741AE7D4C521410D9D714D1D23B25B6C6FDF80E101DE6C2503582E7

Generation used fonttools 4.66.0. The local maintenance command is:

    python tools/subset_font.py

GLYPHS.txt records the exact Unicode character set used by both subsets. The
source fonts are intentionally not committed because each is about 16 MB.
