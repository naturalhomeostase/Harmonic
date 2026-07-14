# Harmonic 🎵

Player de música local para Android — rápido, offline, sem anúncios.
Inspirado no Poweramp, com interface Material You.

## Status: MVP (fase 1)

O que já está implementado nesta base:

- ✅ Escaneamento automático da biblioteca via `MediaStore` (com observador de mudanças em tempo real)
- ✅ Banco local com Room (músicas, playlists)
- ✅ Reprodução em segundo plano via Media3 (`MediaSessionService` + `ExoPlayer`)
  - Notificação com controles, tela bloqueada, resposta a botões Bluetooth/fone,
    pausa automática ao desconectar áudio — tudo de graça via Media3, sem código manual
- ✅ Tela de Biblioteca (abas: músicas, artistas, álbuns, gêneros, pastas, favoritas) + busca instantânea
- ✅ Tela "Agora Tocando" com controles grandes, shuffle, repeat, seek
- ✅ Material You (cor dinâmica no Android 12+) + cor de destaque customizável + 5 wallpapers padrão inclusos
- ✅ Suporte nativo a MP3, FLAC, WAV, AAC, OGG, OPUS, M4A (via ExoPlayer)

## O que falta (próximas fases — vamos construir juntos)

- ⬜ Fila de reprodução persistente ("tocar em seguida", salvar/restaurar fila)
- ⬜ Playlists (criar, editar, importar/exportar M3U)
- ⬜ Equalizador de 10 bandas + Bass Boost + Virtualizador + Reverb (via `android.media.audiofx`)
- ⬜ Letras sincronizadas (LRC), offline e busca online
- ⬜ Widgets (pequeno/médio/grande) via Glance
- ⬜ Editor de tags
- ⬜ Sleep timer
- ⬜ Crossfade + ReplayGain
- ⬜ Android Auto
- ⬜ Visualizador de espectro/ondas
- ⬜ Detecção de duplicatas e arquivos quebrados
- ⬜ A-B Repeat, marcadores, "Wrapped" anual

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
