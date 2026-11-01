# Platea Home

Un launcher per Android TV e Google TV in stile tvOS: minimale, senza pubblicità, senza accesso a internet.
È il launcher di [CleanYourTV](https://github.com/Gathin23/cleanyourtv) con uno stile diverso, compilato ogni volta
sopra **l'ultima versione** di CleanYourTV: il debloat, gli elenchi per modello, l'installazione sicura della Home e il
ripristino restano quelli di CleanYourTV.

Design: il mockup "Platea Home" (Home, TV e ingressi, App, Sipario, pannelli). Font Figtree, palette Grafite.

## Come funziona
`platea-home.sh build`:
1. scarica o aggiorna CleanYourTV in `.cache/` e ne estrae la versione richiesta (di serie l'ultima) in `build/cyt/`;
2. copia sopra `overlay/` (file nuovi o sostituiti: font, testi, codice nuovo);
3. applica `patches/apply.py`, le poche modifiche al codice di CleanYourTV: ognuna deve trovare il suo punto esatto;
4. compila con Gradle.

Se l'ultima versione di CleanYourTV non si applica o non compila, lo script torna da solo all'ultima versione buona
(`state/last-good`), così la TV non resta mai senza Home. Prima di compilare mostra cosa è cambiato nel launcher upstream.

| Comando | Cosa fa |
|---|---|
| `./platea-home.sh build` | Compila sopra l'ultima CleanYourTV (`CYT_REF=<commit>` per una versione precisa) |
| `TV_HOST=<ip> ./platea-home.sh install` | Compila e installa con `build-and-install.sh` di CleanYourTV (`STOCK_LAUNCHER=none` se la Home attuale non è un launcher di serie) |
| `TV_HOST=<ip> ./platea-home.sh theme [cartella]` | Invia il tema (di serie `theme/`) |
| `./platea-home.sh status` | Versione di CleanYourTV usata e ultima disponibile |

Debloat e ripristino: `tools/disable.sh` e `tools/restore.sh` di CleanYourTV, con l'elenco del proprio modello.

## Cosa cambia rispetto a CleanYourTV
| Parte | Come |
|---|---|
| Stile (dock a 6, top shelf testuale, focus bianco, Grafite, pulsanti solo icona) | `theme/config.json`, chiavi documentate di CleanYourTV |
| Font Figtree | `overlay/.../res/font/outfit.ttf` sostituito (licenza OFL in `overlay/launcher/`) |
| Una riga sotto il nome dell'app ("La tua libreria, in direct play.") | `PlateaHomeText.java` + `platea_home.xml` nuovi, una sola inserzione in `MainActivity.updateHeroMeta` |
| Nome del pacchetto | `APP_ID=...` (proprietà `launcherApplicationId` di CleanYourTV). Di serie resta `com.gathin.tvhome`, così funziona l'installer di CleanYourTV |

Da fare: riga di categoria sopra il nome, pannelli laterali (rapido, rete, ingressi), impostazioni, linea del Sipario.

## Requisiti
JDK 17 (`JAVA_HOME`), Android SDK con platform 34 e build-tools 34.0.0, Python 3, `adb`.

## Licenze
- CleanYourTV: MIT, © 2026 Gathin. Il codice estratto in `build/` mantiene la sua licenza.
- Figtree: SIL Open Font License 1.1, © The Figtree Project Authors.
- Overlay, patch e script di questo repo: MIT (`LICENSE`).

Per una versione pubblica: pacchetto rinominato (`APP_ID`), un installer nostro con gli stessi passaggi sicuri e una riga
in `SHOWCASE.md` di CleanYourTV, come chiedono i loro `AGENTS.md`.
