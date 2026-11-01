# Platea Home

Un launcher per Android TV e Google TV in stile tvOS: minimale, senza pubblicità, **senza accesso a internet**.
Il debloat non è nostro: è quello di [CleanYourTV](https://github.com/Gathin23/cleanyourtv), con i suoi elenchi testati
per modello, preso sempre all'ultima versione. Platea Home fa solo da Home.

Design: il mockup "Platea Home" (Home, TV e ingressi, App, Sipario, pannelli). Font Figtree, palette Grafite.

## Cosa c'è (versione 0.1)
- **Home**:
  - ora e data, con tre pulsanti tondi: Ingressi, Rete, Impostazioni;
  - top shelf testuale: categoria, nome dell'app, una riga;
  - dock a 6 app, con il focus bianco che solleva la tessera;
  - una riga attenuata con le altre app e "Tutte le app".
- **OK tenuto premuto** su un'app (oppure il tasto Menu): Sposta, Togli dal dock / Aggiungi al dock, Nascondi, Info app.
- **Sposta**: l'app si solleva; Sinistra e Destra la spostano, OK la posa, Indietro annulla.
- **App**: tutte le app, 6 per riga. Le app del dock hanno un puntino, quelle nascoste sono attenuate.
- **Sipario**: salvaschermo con ora, linea e data su nero, che si sposta un poco ogni minuto.
- **Peso**: APK di circa 72 KB. Sulla Sony BRAVIA VH1 la Home usa circa 22 MB di RAM a regime.

Da fare: pannelli laterali (rapido, Rete, Ingressi), impostazioni del launcher sulla TV, palette scelta dalla TV.

## Comandi
| Comando | Cosa fa |
|---|---|
| `./platea-home.sh build` | Compila `build/platea-home.apk` (`CONFIG=<json>` per la configurazione della TV) |
| `TV_HOST=<ip> ./platea-home.sh install` | Installa e rende Home in modo sicuro (`SIPARIO=1` imposta anche il salvaschermo) |
| `TV_HOST=<ip> ./platea-home.sh debloat <elenco>` | Debloat con `tools/disable.sh` dell'ultima CleanYourTV (es. `examples/sony-bravia-4k-vh1/debloat.txt`) |
| `TV_HOST=<ip> ./platea-home.sh restore` | Ridà la Home e il salvaschermo di prima, riattiva i launcher di serie disattivati, rimuove Platea Home |
| `TV_HOST=<ip> ./platea-home.sh status` | Home attuale, versione, memoria |

**Installazione sicura:**
1. Platea Home deve aprirsi davvero (`am start -W`) prima di diventare la Home.
2. Un launcher di serie Google viene disattivato solo se la TV continua a preferirlo.
3. Se la Home non passa a Platea Home, torna quella di prima.

Lo stato per ogni TV (Home e salvaschermo di prima, launcher disattivati) è in `state/<ip>/`.

## Configurazione
Un file JSON messo nell'APK alla build (`config/default.json` di serie):

| Chiave | Significato |
|---|---|
| `dock` | Le app del dock, in ordine (massimo 6; quelle non installate si saltano) |
| `hidden` | App da non mostrare nella riga della Home (restano nella pagina App) |
| `live_tv` | App della TV in diretta, usata dal pulsante Ingressi se la TV non ha il selettore di sistema |
| `palette` | `Grafite`, `Notte blu`, `Giorno` |
| `sipario_palette` | `Nero`, `Grafite`, `Notte blu` |
| `lines` | `{"pacchetto": ["CATEGORIA", "riga"]}` in aggiunta a quelle di serie (`Config.java`) |

Le scelte fatte sulla TV (ordine del dock, app nascoste) restano sulla TV e vincono sul config.

## Build
Senza Gradle: `aapt2`, `javac`, `d8`, `apksigner` (`build.sh`). Servono:
- JDK 17 (`JAVA_HOME`, di serie Corretto 17);
- Android SDK con `platforms;android-34` e `build-tools;36.0.0` (`ANDROID_HOME`, di serie quello di Homebrew).

La chiave di firma si crea al primo build in `keys/platea-home.keystore`. **Non è su GitHub**: va tenuta in un posto
sicuro, perché senza la stessa chiave l'app installata non si aggiorna e andrebbe disinstallata.

## Licenze
- Codice di questo repo: MIT (`LICENSE`).
- Figtree: SIL Open Font License 1.1, © The Figtree Project Authors (`app/assets/fonts/FIGTREE-OFL.txt`).
- CleanYourTV (usato solo per il debloat, non incluso): MIT, © Gathin.
