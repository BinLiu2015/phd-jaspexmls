/*
 * jaspex-mls: a Java Software Speculative Parallelization Framework
 * Copyright (C) 2015 Ivo Anjo <ivo.anjo@ist.utl.pt>
 *
 * This file is part of jaspex-mls.
 *
 * jaspex-mls is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jaspex-mls is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jaspex-mls.  If not, see <http://www.gnu.org/licenses/>.
 */

package jaspex.speculation;

import jaspex.Options;
import jaspex.speculation.newspec.*;
import jaspex.util.ShellColor;

import java.io.PrintWriter;
import java.util.*;

import asmlib.*;
import asmlib.extra.*;

import util.UtilList;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

/** Classe que efectua as transformações para um programa poder ser executado usado especulação **/
public class SpeculativeTransformer {

	private InfoClass currentClass;
	private boolean _JDKClass;

	public SpeculativeTransformer(boolean JDKClass) {
		_JDKClass = JDKClass;
	}

	private void harvestInfoClass(ClassReader cr) {
		// Popular informação da classe
		currentClass = new InfoClass(cr.getClassName(), cr.getSuperName());
		cr.accept(new InfoClassAdapter(currentClass), 0);
	}

	public byte[] transform(byte[] classBytes) {
		ClassReader cr = new ClassReader(classBytes);
		harvestInfoClass(cr);

		byte[] output;

		// Criar métodos para execução especulativa
		output = createSpeculativeMethods(cr);

		// Remover LINENUMBER
		if (Options.NOLINENUMBER) output = removeLineNumber(output);

		if (!_JDKClass) {
			// Inserir código para especulação
			cr = new ClassReader(output);
			harvestInfoClass(cr);

			output = insertSpeculationCode(cr);
		}

		if (Options.PRINTCLASS) {
			Textifier t = new Textifier() {
				@Override
				public Textifier visitMethod(int access, String name, String desc,
						String signature, String[] exceptions) {
					String color;

					if (name.endsWith("$speculative") ||
						(name.equals("<init>") &&
						 desc.contains(CommonTypes.SPECULATIVECTORMARKER.bytecodeName()))) {
						color = "48;5;69;38;5;15";
					} else if (name.endsWith("$non_speculative") ||
						(name.equals("<init>") &&
						 desc.contains(CommonTypes.NONSPECULATIVECTORMARKER.bytecodeName()))) {
						color = "48;5;70;38;5;15";
					} else {
						color = "48;5;90;38;5;15";
					}

					text.add(ShellColor.startColor(color));
					Textifier ret = super.visitMethod(access, name, desc, signature, exceptions);
					text.add(text.size() - 1, ShellColor.endColor() + '\n');
					return ret;
				}
			};
			new ClassReader(output).accept(
				new TraceClassVisitor(null, t, new PrintWriter(System.out)), 0);
		}

		if (Options.WRITECLASS) {
			try {
				new java.io.File("output" + java.io.File.separatorChar).mkdir();
				java.io.FileOutputStream fos = new java.io.FileOutputStream("output" +
					java.io.File.separatorChar + currentClass.type().commonName() + ".class");
				fos.write(output);
				fos.close();
			} catch (java.io.IOException e) { throw new Error(e); }
		}

		// Passar verificador do ASM pelo output
		jaspex.transactifier.Transactifier.checkBytecode(output);

		return output;
	}

	public static byte[] removeLineNumber(byte[] output) {
		ClassWriter cw = new ClassWriter(0);

		new ClassReader(output).accept(new ClassVisitor(Opcodes.ASM4, cw) {
			@Override public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions) {
				return new MethodVisitor(Opcodes.ASM4,
					cv.visitMethod(access, name, desc, signature, exceptions)) {
					@Override public void visitLineNumber(int line, Label start) { }
				};
			}
		}, 0);

		return cw.toByteArray();
	}

	public byte[] createSpeculativeMethods(ClassReader cr) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		ClassVisitor cv = cw;

		// Tornar classe public: para o codegen funcionar, todas as classes têm que ser públicas
		// (todos os métodos $speculative também já são public)
		if (!_JDKClass) cv = new ClassVisitor(Opcodes.ASM4, cv) {
			@Override public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
				access = access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;
				cv.visit(version, access, name, signature, superName, interfaces);
			}
		};

		// Visitor que adiciona fields e métodos para o -detectlocal
		cv = new InjectDetectLocalClassVisitor(cv);

		// Criar versões $speculative de métodos
		cv = new GenericMethodVisitorAdapter(cv, CreateSpeculativeMethodVisitor.class, currentClass, _JDKClass);

		// Marcar constructores com SpeculativeCtorMarker
		cv = new GenericMethodVisitorAdapter(cv, SpeculativeCtorMethodVisitor.class);

		// Potencialmente substituir alguns dos métodos com versões internas do JaSPEx
		cv = new GenericMethodVisitorAdapter(cv, MethodReplacerMethodVisitor.class, currentClass, _JDKClass);

		// Resolver problema com o acesso a métodos privados
		cv = new GenericMethodVisitorAdapter(cv, FixPrivateMethodAccessMethodVisitor.class, currentClass, _JDKClass);

		// Injectar overloads para a todos os métodos que são herdados de superclasses não-transactificáveis
		// Nota: Deve estar na pipeline *antes* do CreateSpeculativeMethodVisitor
		if (!_JDKClass) cv = new InjectOverloadsClassVisitor(cv, currentClass);

		cr.accept(cv, 0);

		// Criar versões non-speculative dos métodos, que funcionam transaccionalmente, mas não
		// fazem spawn de especulações
		new ClassReader(cw.toByteArray()).accept(new CreateNonSpeculativeMethodsClassVisitor(cw), 0);

		return cw.toByteArray();
	}

	public byte[] insertSpeculationCode(final ClassReader cr) {
		ClassVisitor cv;

		// 1º Pass: Fazer tentativamente modificações para especulação, concretizar todos os Futures,
		//	    e guardar o resultado num ClassNode
		ClassNode firstPassNode = new ClassNode();
		cv = firstPassNode;

		// Colocação das chamadas ao get do future
		cv = new GenericMethodVisitorAdapter(cv, DelayGetFutureMethodVisitor.class, currentClass);
		// Modificações para chamar o spawnSpeculation
		cv = new GenericMethodVisitorAdapter(cv, InsertContinuationSpeculationMethodVisitor.class, currentClass);

		cr.accept(cv, ClassReader.EXPAND_FRAMES);

		// 1.5º Pass: Detectar overspeculation -- casos onde a distância entre a especulação e a sua
		//	      concretização é demasiado pequena
		Map<InfoMethod, UtilList<Integer>> rejectedSpecIdsMap = new HashMap<InfoMethod, UtilList<Integer>>();
		RemoveOverspeculation.scanOverspeculation(currentClass, firstPassNode, rejectedSpecIdsMap);

		ClassNode removeOverspecNode;
		ClassReader finalPassReader;
		// Nota: Originalmente não era efectuado um loop aqui. O loop é feito porque o
		//	 RemoveOverspeculation que é executado depois do FixFutureMultipleControlFlows pode
		//	 detectar casos adicionais de overspeculation, e portanto temos que repetir o processo
		//	 para os excluir.
		//	 Além disso, o processo pode demorar várias iterações a convergir, como no caso do
		//	 NewSpecExample72.test17() em que na primeira iteração é detectada overspeculation num
		//	 dos casos, o código é re-gerado sem essa especulação, e só no final da segunda
		//	 iteração é que é detectado que os restantes casos também são overspeculation
		//	 (e portanto é feita uma terceira iteração que será a final, naquele caso).
		//	 Finalmente, de notar que o RemoveOverspeculation é opcional, ou seja o ciclo até pode
		//	 ser comentado, voltando à versão anterior em que o código só fazia uma iteração.
		do {

		// 2º Pass: Repetir modificações, tomando em conta os resultados do RemoveOverspeculation
		ClassNode secondPassNode = new ClassNode();
		cv = secondPassNode;

		// Adicionar NOPs antes de todas as Labels, para facilitar o trabalho do FixFutureMultipleControlFlows
		cv = new GenericMethodVisitorAdapter(cv, NopAddBeforeLabelMethodVisitor.class);
		// Modificações para chamar o spawnSpeculation
		cv = new GenericMethodVisitorAdapter(cv, InsertContinuationSpeculationMethodVisitor.class, currentClass, rejectedSpecIdsMap);

		cr.accept(cv, ClassReader.EXPAND_FRAMES);

		// 3º Pass: Detecção e resolução de problemas devido a Futures e múltiplos fluxos de controlo
		// 	    e geração de lista de frames do método a usar no DelayGetFutureMethodVisitor

		// Obter versão original dos métodos antes das alterações para especulação, para que o
		// computeControlFlowGraph possa fazer revert caso não consiga corrigi-los
		ClassNode origMethods = new ClassNode();
		cr.accept(origMethods, ClassReader.EXPAND_FRAMES);
		FixFutureMultipleControlFlows.computeControlFlowGraph(secondPassNode, origMethods);

		// 4º Pass: Gerar classe final, limpar metadados extra
		ClassWriter cw = new jaspex.util.ClassWriter(ClassWriter.COMPUTE_FRAMES);
		cv = cw;

                // Remover NOPs de novo
                cv = new GenericMethodVisitorAdapter(cv, NopRemoveMethodVisitor.class);
		// Colocação das chamadas ao get do future
		cv = new GenericMethodVisitorAdapter(cv, DelayGetFutureMethodVisitor.class, currentClass);

		// Gerar classe a partir do ClassNode
		secondPassNode.accept(cv);

		finalPassReader = new ClassReader(cw.toByteArray());

		// 4.5º Pass: Re-executar RemoveOverspeculation. Isto pode originar resultados diferentes do
		//	      pass original porque esse é executado sem frames correctas e sem o
		//	      FixFutureMultipleControlFlows (ver comentário antes do ciclo acima)
		removeOverspecNode = new ClassNode();
		finalPassReader.accept(removeOverspecNode, 0);

		} while (RemoveOverspeculation.scanOverspeculation(currentClass, removeOverspecNode, rejectedSpecIdsMap));

		// 5º Pass: Hack: Se houverem labels diferentes mas que estão seguidas (como no NewSpecExample8),
		//	    o RemoveUnusedTryCatchBlockMethodVisitor não funciona correctamente. Uma nova passagem
		//	    por um ClassWriter elimina labels repetidas.
		//	    A alternativa a fazer esta passagem seria criar um MethodVisitor que removesse labels
		//	    repetidas.
		ClassWriter cw = new jaspex.util.ClassWriter(ClassWriter.COMPUTE_FRAMES);
		cv = cw;

		// Retirar informação extra que é passada dentro do tipo dos futures
		cv = new GenericMethodVisitorAdapter(cv, CleanupFutureTypeInfoMethodVisitor.class);
		// Remover marcadores de inlining da transactificação
		cv = new GenericMethodVisitorAdapter(cv, RemoveInlineMarkerMethodVisitor.class);
		// Remover entradas na exception table não usadas
		cv = new GenericMethodVisitorAdapter(cv, RemoveUnusedTryCatchBlockMethodVisitor.class);
		// (Potencialmente) corrigir problemas com blocos try/catch vazios (BUG/LIMITAÇÃO ASM)
		cv = new GenericMethodVisitorAdapter(cv, FixDeadTryCatchBlockMethodVisitor.class);

		finalPassReader.accept(cv, 0);

		return cw.toByteArray();
	}

}
