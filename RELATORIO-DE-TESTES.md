# Nox 1.0.0 — relatório de verificação

Distribuição para Android 10 ou superior. Pacote `br.app.nox`, versionCode 1. O APK de produção deriva da compilação 45903475785, commit `492fe8cb0762f859a527380412327b757afedcdc`. APK SHA-256 `d21211d581738c2930cd1f28766cf8b1939cfe32e93e874d54fb55b3d656d0a1`.

| Ensaio | Resultado observado |
| --- | --- |
| Assinatura do APK distribuído | Android SDK apksigner validou o esquema v2 e o certificado local. |
| APK de produção no Android 10 | Instalação concluída pelo Package Manager. Activity inicial e botão de configuração verificados com modo avião habilitado, Wi-Fi e dados móveis desligados. |
| Android 15, variante de desenvolvimento | Quatro abas abertas e capturadas nos temas claro e escuro. PDF multipágina gerado e aberto pelo PdfRenderer. Escrita e leitura SQLite, exportação e restauração do JSON temporário passaram. |
| Alarme no Android 15 | Com o app em segundo plano e uma URI indisponível, o sistema registrou reprodução com USAGE_ALARM. Houve soneca encurtada até o limite, segundo disparo e interrupção do áudio pelo botão Despertei. A saída da cama continuou pendente. A reprodução foi observada pela API de áudio, sem medição acústica. |
| Reinicialização e Doze | Alarme armado, emulador reiniciado e desbloqueado uma vez. Após solicitação de encerramento do processo em segundo plano, tela apagada e Doze forçado, o serviço de alarme voltou ao primeiro plano no prazo do teste. |
| Algoritmo e calendário | 27.694 verificações do algoritmo e casos de recorrência semanal, pulo de ocorrência, horário repetido na troca de fuso, sonecas e limites no cálculo de eficiência. |
| Android Lint | A etapa passou após proteger a recepção da atualização da tela de disparo nas versões antigas do Android. |

### Correções durante o desenvolvimento

Corrigidos arredondamento da mediana de horários anteriores à referência, busca da próxima ocorrência semanal após pular um dia e retorno à noite parcial criada pelo despertador convencional. O ensaio de interface passou a aguardar a janela e a coleta passou a reconhecer a saída real da instrumentação.

### Limites da validação

Sem aparelho Samsung físico. Ainda requerem teste a suspensão profunda da One UI, Não Perturbe com regras pessoais, volume percebido no alto-falante ou Bluetooth, remoção de recentes em diferentes modelos, TalkBack, consumo de bateria em várias noites e personalização longitudinal em uso real. Nenhum ensaio clínico avaliou se o algoritmo reduz o cansaço. A simulação de reinício observou a volta do serviço. O teste de áudio e soneca constitui um ensaio separado.

[Android 15 e reinício](https://github.com/davikdx/NOX/actions/runs/45903961832) · [Assinatura](https://github.com/davikdx/NOX/actions/runs/45903961970) · [Instalação do APK de produção no Android 10](https://github.com/davikdx/NOX/actions/runs/45904416017)
