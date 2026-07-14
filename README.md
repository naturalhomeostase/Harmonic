# Harmonic 🎵

Player de música local para Android — rápido, offline, sem anúncios.
Inspirado no Poweramp, com interface Material You.

## Status: Fase 3 em andamento

Adicionado nesta leva:

- ✅ Capa real do álbum (embutida no arquivo de áudio), com cache em memória —
  aparece na Biblioteca, no mini player e em "Agora Tocando"; cai num ícone
  de nota musical quando a música não tem capa
- ✅ Letras sincronizadas (LRC) offline — procura automaticamente um arquivo
  `.lrc` (sincronizado) ou `.txt` (simples) com o mesmo nome da música, na
  mesma pasta; auto-scroll e destaque da linha atual em "Agora Tocando"
- ✅ Widget de tela inicial (Glance) — mostra música atual + play/pause/
  próxima/anterior, atualiza sozinho quando a música troca

## ⚠️ Nota de confiança sobre o widget

O widget foi escrito usando a API do Glance (`androidx.glance:glance-appwidget`),
que é bem menos comum que o Compose "normal" — por isso é a parte deste PR
com **menor confiança de compilar de primeira**. Se o próximo build falhar
especificamente em arquivos dentro de `widget/`, é o candidato nº 1 a
investigar (nomes de parâmetros de `Row`/`Column`/`ColorProvider` no Glance
podem estar levemente diferentes da versão 1.1.0 real). O resto do projeto
(capa do álbum, letras) usa só Compose/Coil/MediaStore padrão, mais testado.

## O que falta (continuando a fase 3)

- ⬜ Crossfade + ReplayGain (mixagem de fato entre faixas)
- ⬜ Android Auto (requer migrar de `MediaSessionService` pra `MediaLibraryService`)
- ⬜ Visualizador de espectro/ondas
- ⬜ Detecção de duplicatas e arquivos quebrados
- ⬜ A-B Repeat, marcadores/bookmarks, "Wrapped" anual
- ⬜ Editor de tags
- ⬜ Busca de letras online (a busca offline já funciona)
- ⬜ Presets de equalizador prontos (Rock, Pop, Jazz...)
- ⬜ Widgets em outros tamanhos (hoje só tem um tamanho médio)

## Como compilar

### Opção 1 — Android Studio (recomendado para desenvolvimento)
1. Abra este projeto no Android Studio (Hedgehog ou mais recente).
2. O Studio vai baixar as dependências automaticamente na primeira sincronização.
3. Rode no seu celular (via USB, com depuração USB ativada) ou num emulador.

### Opção 2 — GitHub Actions (gera o APK sem precisar instalar nada)
1. Faça push deste repositório para o GitHub.
2. Vá na aba **Actions** → o workflow `Build APK` roda automaticamente.
3. Ao terminar, baixe o artefato `harmonic-debug-apk` — é o `.apk` pronto pra instalar no celular.

> Nota: o `gradle-wrapper.jar` não foi commitado (é um binário) — o workflow do
> GitHub Actions gera ele automaticamente antes de compilar. Se for abrir no
> Android Studio, ele mesmo cuida disso na sincronização inicial.

## Arquitetura

```
app/src/main/java/com/harmonic/player/
├── MainActivity.kt          # Activity única, hospeda a navegação Compose
├── HarmonicApp.kt           # Application: expõe database e settings
├── data/                    # Room (Song, Playlist), MediaStoreScanner, SettingsRepository (DataStore)
├── playback/                # PlaybackService (Media3) + PlayerController (ponte com a UI)
└── ui/
    ├── theme/               # Material You + cor de destaque customizável
    ├── library/              # Tela de biblioteca
    ├── nowplaying/            # Tela "Agora Tocando"
    ├── settings/              # Aparência (cor + wallpaper)
    └── common/                # Telas compartilhadas (permissão, etc.)
```

## Papéis de parede inclusos

Os 5 fundos padrão ficam em `app/src/main/assets/default_wallpapers/`:
leão em chamas, guitarra elétrica, toca-discos vintage, floresta encantada, cidade lo-fi.
