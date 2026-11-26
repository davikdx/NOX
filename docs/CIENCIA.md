# Sono, diário e critérios do despertar

O Nox foi concebido para adultos que desejam observar sua rotina. Um despertador precisa saber a hora em que deve tocar. Um diário pode descrever como a pessoa percebeu aquela noite. A combinação permite estudar hábitos, mas a hora de dormir e a hora de acordar não revelam o estágio do sono no instante do alarme.

## O que sustenta as escolhas

| Tema | Base e implicação |
|---|---|
| Fases e ciclos | A sequência NREM–REM varia entre pessoas e dentro da noite. O NHLBI descreve recorrência típica em torno de 80 a 100 minutos, com maior concentração de sono profundo no início e de REM no fim. “Ciclos teóricos” no aplicativo equivalem à duração prevista dividida por 90. O número pode ser fracionário e tem função ilustrativa. [NHLBI, Sleep Phases and Stages](https://www.nhlbi.nih.gov/health/sleep/stages-of-sleep) |
| Duração e privação | Para adultos saudáveis de 18 a 60 anos, AASM e SRS recomendam regularmente pelo menos sete horas. Há variação individual. A configuração inicial de oito horas é uma meta editável. Uma estimativa não atesta que a pessoa efetivamente dormirá por esse tempo. [Watson et al., Sleep, 2015](https://pubmed.ncbi.nlm.nih.gov/26039963/) |
| Regularidade e ritmo circadiano | O relógio circadiano e a pressão homeostática participam da propensão ao sono. A regularidade das horas de deitar e levantar é uma dimensão relevante da saúde do sono. O horário civil habitual é uma descrição comportamental, não uma medida da fase circadiana. Os dados de plantões podem divergir do padrão de dias úteis e fins de semana. [NHLBI, What Makes You Sleep?](https://www.nhlbi.nih.gov/health/sleep-deprivation/body-clock) · [Sletten et al., Sleep Health, 2023](https://pubmed.ncbi.nlm.nih.gov/37684151/) |
| Inércia do sono | A redução transitória do desempenho após acordar sofre influência da privação prévia e do horário circadiano, entre outros fatores. Fadiga na manhã seguinte não identifica um despertar em sono profundo nem prova que um alarme causou um sintoma. [Hilditch e McHill, 2019](https://pubmed.ncbi.nlm.nih.gov/31692489/) |
| Diário, latência e despertares | O Consensus Sleep Diary fornece base conceitual para separar intenção de dormir, latência, tempo acordado após o início, despertar final, saída da cama e qualidade percebida. O Nox adapta os campos para registro pessoal. A latência é uma estimativa da pessoa ou da configuração. [Carney et al., Sleep, 2012](https://pubmed.ncbi.nlm.nih.gov/22294820/) |
| Fadiga, qualidade e sonolência | A qualidade subjetiva, a fadiga e a propensão a adormecer são dimensões distintas. O Nox coleta separadamente esses relatos. A escala diurna de cinco categorias é própria, sem alegação de equivalência à Karolinska ou à Epworth. É possível repetir a avaliação ao longo do dia. [AHA, Multidimensional Sleep Health, 2025](https://pmc.ncbi.nlm.nih.gov/articles/PMC12147655/) |

## Seleção de horários

1. O sistema resolve o horário-limite para a próxima ocorrência civil **após o horário de deitar**. Um período iniciado à manhã após um plantão pode terminar à tarde do mesmo dia. Uma sessão em que o limite já passou usa a próxima ocorrência. A confirmação mostra data e hora. Na sobreposição de horário de verão, usa-se o primeiro instante válido ainda futuro. Em um salto de horário, usa-se o primeiro instante válido após a lacuna.
2. A oportunidade prevista de sono em minutos é o intervalo real até o despertar escolhido menos a latência. Um valor negativo é exibido como zero. A previsão prévia não tem informação sobre a duração das vigílias que ainda ocorrerão.
3. O horário-limite é a recomendação inicial. Antecipação automática exige referência habitual consistente ou um horário habitual inserido pelo usuário. A recomendação avança na direção dessa referência apenas se aproximá-la em pelo menos cinco minutos sem reduzir a previsão abaixo de sete horas nem abaixo da meta pessoal. A antecipação automática fica limitada ao menor valor entre a janela configurada e quinze minutos.
4. Se duas escolhas próximas estiverem na direção do padrão habitual e o ajuste estiver limitado pela tolerância de antecipação ou pela meta, a mediana das manhãs com qualidade boa/excelente e fadiga leve/ausente pode ajustar um minuto. Esse é um critério descritivo. O app não testa causalidade nem prevê disposição individual.
5. No modo assistido, a pessoa pode escolher o limite, a opção recomendada ou opções adicionais dentro da janela. Para ordenar a alternativa por ciclos, primeiro separam-se as opções que preservam sete horas e a meta. Só depois considera-se a proximidade aritmética a um múltiplo de 90 minutos. Esse cálculo não pode adiantar sozinho o alarme automático.
6. A sugestão inversa de deitar corresponde ao limite menos meta e latência. Uma segunda opção reserva mais trinta minutos. O intervalo extra é uma preferência de planejamento, sem pretensão de representar um ciclo biológico.

As escolhas de limiar (sete registros, quinze minutos máximos automáticos, cinco minutos mínimos de aproximação) são decisões de engenharia conservadoras. Nenhum estudo citado validou o algoritmo como um despertador clínico.

## Histórico e personalização

O perfil examina até as trinta noites elegíveis mais recentes nos noventa dias anteriores ao limite. A mediana reduz o efeito de registros isolados. As noites precisam ter um despertar declarado, deitar confirmado e duração cronologicamente plausível. Planejar um horário e deixar de confirmá-lo não fornece evidência para aprendizado. A separação conforme o dia do **despertar** (segunda a sexta ou sábado e domingo) entra em vigor quando o mesmo grupo tem pelo menos sete observações. Antes disso, os grupos são reunidos.

A latência pessoal usa a mediana de pelo menos sete relatos diretos ou faixas fechadas. As faixas fechadas são aproximadas por 3, 10, 23 ou 45 minutos. “Mais de 60 minutos” e “não sei” não produzem um ponto médio. A faixa original permanece no diário. Latências de até seis horas entram no perfil; relatos mais longos podem ser registrados, mas não determinam o horário sugerido. Sem observações suficientes, aplica-se a latência configurada, inicialmente 20 minutos.

Para horários, a mediana opera nos desvios circulares em relação ao limite. Assim 23h59 e 00h01 continuam próximos. Períodos a mais de seis horas da referência configurada são excluídos do perfil de horário para evitar misturar turnos opostos. A referência automática requer no mínimo sete horários elegíveis e desvio absoluto mediano de até trinta minutos. O perfil oferece ajustes manuais, desativação e restauração. O desligamento do aprendizado conserva o diário.

## Significado dos registros

- **Automático.** Horários de disparo, duração programada da soneca e marca temporal dos comandos “Vou dormir agora”, “Despertei” e “Levantei”. O significado dos dois últimos comandos é uma declaração da pessoa. Dispensar um alarme por outro meio não confirma saída da cama.
- **Planejado.** Um horário futuro para deitar é gravado como planejado. O alarme já pode ser armado, mesmo se o telefone ficar sem interação. Tocar em “Vou dormir agora” recalcula o despertar e registra o horário de fato confirmado.
- **Autorreferido.** Latência relatada, duração da vigília noturna, sintomas, qualidade, sonolência e correções posteriores. Falta de resposta permanece distinta de zero. Na contagem de despertares, quatro indica “quatro ou mais” e menos um indica “não me lembro”.
- **Calculado.** Início estimado do sono, duração, regularidade e proporção estimada de tempo dormindo. Uma duração baseada na latência configurada ou sem vigília informada pode superestimar o sono. O relatório deve sinalizar esses limites. A eficiência requer informações de latência e vigília e tempo válido entre deitar e levantar.
- **Sonolência do dia.** Cada avaliação liga-se ao despertar declarado mais recente, se ocorreu nas trinta horas anteriores. Sem noite elegível, a resposta permanece registrada com data e hora. O limite acomoda turnos e não tem significado diagnóstico.

Sintomas e desempenho diurno podem variar com turnos, disponibilidade de tempo para dormir, medicamentos, ambiente ou doenças. Registros irregulares e perguntas puladas introduzem viés de seleção. Um padrão persistente de sonolência intensa, cefaleia matinal ou sono pouco reparador pode motivar uma conversa clínica; o diário, por si só, não identifica a causa.
