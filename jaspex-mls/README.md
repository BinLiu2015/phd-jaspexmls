Notas jaspex:
=============

Compilação: Usar o ant

Executar:
  Adicionar directorias necessárias ao CLASSPATH
  $ export CLASSPATH=libs/asm-debug-all-4.0.jar:libs/asmlib.jar:libs/commons-lang3-3.1.jar:libs/logback-classic-1.0.0.jar:libs/logback-core-1.0.0.jar:libs/slf4j-api-1.6.4.jar:libs/:build/

  Especulação
  $ java jaspex.Jaspex [-opções] classe argumentos
  -> Opções especulação:
     -fast : Modo usado para benchmarking, faz menos outputs, faz skip a algumas verificações e optimiza alguns pedaços do código
     -nospeculation : Faz todas as modificações, mas rejeita sempre as especulações
     -printclass : Escreve classes geradas para o stdout
     -writeclass : Escreve classes geradas para a directoria output/ (que tem que já existir)
     -silent : Não faz nenhum output
     -oldspec : Modo antigo de especulação (default é o novo, "newspec")
  -> Para ver todas as opções, passar -help ao jaspex

Source:
  Organizada em:
     jaspex : Coisas globais a todo o projecto, como o main e a gestão das opções.

     jaspex/transactifier : Modificação do bytecode das classes para fazer transactificação de fields.

     jaspex/rulamstm : Implementação de STM usada pelo jaspex. Em tempos baseada na JVSTM, agora bastante diferente
                       e mais inspirada na API de contexts da Deuce.

     jaspex/speculation : Modificação do bytecode das classes para restantes casos de transactificação; escolha e
                          preparação de métodos para especulação, e controlo runtime de especulação.

     jaspex/speculation/newspec : Novo modo de especulação.

     test : Mistura de testcases e pequenas benchmarks. De notar que muitos deles servem para testar as alterações
            de bytecode, portanto fazem pouca ou nenhuma computação e nem fazem trigger de especulações.

     nativegraph : Ainda parte da source tree por motivos históricos, é um programa que faz várias análises
                   estáticas de código e gera gráficos bonitos (que podem ser rendered com o dot do graphviz).

Newspec:
  O "newspec" é o novo modo de especulação. Em vez de especular no inicio de um método, especula a execução da
  continuação de um método.
  Para usar newspec, para além de colocar a libs/contlib.jar no CLASSPATH (ou outra versão da contlib que
  seja compatível), é necessário executar o jaspex com uma JVM que proporcione o suporte para continuações.
