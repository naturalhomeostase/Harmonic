# Changelog — Nodus

Todas as mudanças notáveis do app são registradas aqui, na ordem em que
foram lançadas — a mais recente no topo.

## [1.0.0] — Lançamento inicial

### Segurança
- Senha mestre com derivação de chave via Argon2id e cifragem AES-256-GCM.
- Desbloqueio por biometria (opcional, complementa a senha).
- Bloqueio automático configurável (imediato / 30s / 1 min / 5 min).
- Troca de senha, com re-cifragem automática de todas as notas.
- Bloqueio de capturas de tela e ocultação do conteúdo na tela de apps
  recentes.
- Cópia de texto com limpeza automática da área de transferência.

### Cadernos e notas
- Cadernos personalizáveis: cor sólida, degradê, imagem pronta ou foto
  própria, e escolha de fonte do título.
- Editor de texto rico: formatação, listas, cores, links, alinhamento.
- Estilos de página: em branco, pautada, quadriculada, pontilhada, Cornell.
- Notas fixadas, arquivadas e lixeira.
- Busca por título e conteúdo.

### Backup
- Backup e restauração cifrados, salvos onde o usuário escolher (Google
  Drive ou qualquer outro destino), sem exigir login dentro do app.
- Mensagens de erro específicas na restauração (senha errada, arquivo
  inválido, versão incompatível).

### Outros
- Tela "Sobre" com versão, algoritmos usados, política de privacidade e
  licenças de terceiros.

---

*Formato inspirado em [Keep a Changelog](https://keepachangelog.com/pt-BR/).*
