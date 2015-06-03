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

package jaspex.speculation.nsruntime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asmlib.Type;

/** Factory de Predictors usados para RVP **/
public class PredictorFactory {

	private static final Logger Log = LoggerFactory.getLogger(PredictorFactory.class);

	private static final Object[][] initialValues = {
		{Type.PRIM_BOOLEAN, false}, {Type.OBJECT_BOOLEAN, false},
		{Type.PRIM_BYTE, Byte.valueOf((byte) 0)}, {Type.OBJECT_BYTE,  Byte.valueOf((byte) 0)},
		{Type.PRIM_SHORT, Short.valueOf((short) 0)}, {Type.OBJECT_SHORT, Short.valueOf((short) 0)},
		{Type.PRIM_INT, 0}, {Type.OBJECT_INTEGER, 0},
		{Type.PRIM_LONG, 0l}, {Type.OBJECT_LONG, 0l},
		{Type.PRIM_FLOAT, 0.0f}, {Type.OBJECT_FLOAT, 0.0f},
		{Type.PRIM_DOUBLE, 0.0d}, {Type.OBJECT_DOUBLE, 0.0d},
		{Type.STRING, ""}
	};

	public static Predictor newPredictor(String bytecodeType) {
		Log.debug("PredictorFactory newPredictor({})", bytecodeType);

		Type type = Type.fromBytecode(bytecodeType);

		Object initialValue = null;

		for (Object[] entry : initialValues) {
			if (type.equals(entry[0])) {
				initialValue = entry[1];
				break;
			}
		}

		return new Predictor(initialValue);
	}

}
