# SDKMAN compatibility

What the weekly [SDKMAN compatibility check](.github/workflows/sdkman-contract.yml) saw.
A row is only added when something changed for a platform: SDKMAN version, LTS versions, newest releases or the result.
*Newest* lists the newest release per LTS line for Temurin / Zulu / Corretto (`-` = none).

| Date | Platform | SDKMAN script | SDKMAN native | LTS (foojay) | Newest (tem / zulu / amzn) | Result |
|---|---|---|---|---|---|---|
| 2026-11-22 | linuxx64 | ? | 0.8.0 (linux x86_64) | 7, 8, 11, 17, 21, 25 | 8: `8.0.514.0+1-tem` / `8.0.514.0+1-zulu` / `8.514.1.1-amzn`<br>11: `11.0.33.1+1-tem` / `11.0.33.1+1-zulu` / `11.0.33.1.1-amzn`<br>17: `17.0.21.1+2-tem` / `17.0.21.1+2-zulu` / `17.0.21.1.2-amzn`<br>21: `21.0.13.1+2-tem` / `21.0.13.1+2-zulu` / `21.0.13.1.2-amzn`<br>25: `25.0.5.1+2-tem` / `25.0.5.1+2-zulu` / `25.0.5.1.2-amzn` | success |
| 2026-11-22 | macOS | ? | - | ? | ? | skipped |
| 2026-11-22 | linuxx64 | 5.24.0 | 0.8.0 (linux x86_64) | 7, 8, 11, 17, 21, 25 | 8: `8.0.514.0+1-tem` / `8.0.514.0+1-zulu` / `8.514.1.1-amzn`<br>11: `11.0.33.1+1-tem` / `11.0.33.1+1-zulu` / `11.0.33.1.1-amzn`<br>17: `17.0.21.1+2-tem` / `17.0.21.1+2-zulu` / `17.0.21.1.2-amzn`<br>21: `21.0.13.1+2-tem` / `21.0.13.1+2-zulu` / `21.0.13.1.2-amzn`<br>25: `25.0.5.1+2-tem` / `25.0.5.1+2-zulu` / `25.0.5.1.2-amzn` | success |
| 2026-11-22 | darwinarm64 | 5.24.0 | 0.8.0 (macos aarch64) | 7, 8, 11, 17, 21, 25 | 8: - / `8.0.512.0+9-zulu` / `8.514.1.1-amzn`<br>11: `11.0.32.1+1-tem` / `11.0.33.0+10-zulu` / `11.0.33.1.1-amzn`<br>17: `17.0.21.0+9-tem` / `17.0.21.0+9-zulu` / `17.0.21.1.2-amzn`<br>21: `21.0.13.1+2-tem` / `21.0.13.0+9-zulu` / `21.0.13.1.2-amzn`<br>25: `25.0.5.1+2-tem` / `25.0.5.0+8-zulu` / `25.0.5.1.2-amzn` | success |
| 2026-11-23 | linuxx64 | 5.24.0 | 0.8.0 (linux x86_64) | ? | ? | failure |
| 2026-11-23 | darwinarm64 | 5.24.0 | 0.8.0 (macos aarch64) | ? | ? | failure |
