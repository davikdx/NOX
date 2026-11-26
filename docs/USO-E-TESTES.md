# Instalação no Galaxy
Baixe o APK assinado da compilação desejada. Nos Meus Arquivos, toque nele, autorize a instalação por essa origem se solicitado e conclua. No primeiro uso, abra **Configurar o despertador**, conceda acesso a alarmes exatos, notificações e alarmes em tela cheia. Ajuste o volume do canal Alarme e agende o disparo de teste. Bloqueie a tela e verifique música, adiamento e encerramento. Em Configurações → Bateria → Limites de uso em segundo plano, confira se o Nox está em suspensão profunda. Os menus variam entre versões da One UI.

O aparelho deve estar ligado. Após reiniciar, desbloqueie-o uma vez para que o Nox recupere os alarmes da base protegida. Uma interrupção forçada nas configurações exige reabrir o app. O diagnóstico distingue permissões, volume e acesso ao arquivo de música. Acesse o Nox pela notificação ou pelo ícone para controlar um disparo. Um alarme sem resposta para de tocar em 15 minutos.

# Estrutura
Interface nativa Java com Views e componentes Material do sistema, SQLiteOpenHelper em armazenamento privado e operações transacionais. Acesso a arquivos pelo seletor do Android. Dados do diário e relatórios ficam no dispositivo até exportação pelo usuário. Não há permissão de rede. A seleção de Java e APIs do sistema reduz dependências de construção. Veja COMPILACAO.md e CIENCIA.md.

Scheduler calcula ocorrências por dia civil e usa AlarmManager.setAlarmClock com identidade por ocorrência. AlarmReceiver inicia RingService, que usa áudio de alarme e um canal de notificação. RestoreReceiver reage à inicialização, fuso e relógio. Sleep e DiaryStats separam planejamento, sinais declarados e medidas derivadas. A base versão 2 preserva registros da versão 1 e rejeita downgrade destrutivo. Faça backup antes de mudar de aparelho.

# Verificação
qa/run-unit.sh executa o teste de regras de sono no JDK. qa/device-checks.sh utiliza o pacote de desenvolvimento em perfil limpo e coleta resultados e capturas do emulador. O workflow guarda APKs, certificado, SHA-256, relatório instrumental e imagens conforme as etapas concluídas. Consulte o log da execução para saber o que efetivamente passou. A presença dos scripts por si só não comprova um disparo. Frequência de bateria, Doze prolongado, reinício em vários modelos Galaxy, alto-falante real, acessibilidade com TalkBack e funcionamento em noites consecutivas exigem ensaios adicionais.

# Atualizações assinadas
O pacote de distribuição usa br.app.nox. Preserve a chave privada entregue ao titular e o certificado público qa/recovery-public.pem. A chave privada fica fora do GitHub. Para uso com o Android Studio, converta-a para PKCS12 no seu computador. O comando pede uma senha para proteger o novo arquivo.
```sh
openssl pkcs12 -export -name nox -inkey Nox-recuperacao-privada.pem -in qa/recovery-public.pem -out nox.p12
```
Configure o Gradle como indicado em COMPILACAO.md e use a mesma chave com um versionCode maior. A variante de desenvolvimento usa br.app.nox.dev e uma chave de depuração separada.