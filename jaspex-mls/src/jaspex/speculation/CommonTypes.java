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

import asmlib.Type;

public class CommonTypes {

	public static final Type NONSPECULATIVECTORMARKER =
		Type.fromClass(jaspex.speculation.runtime.NonSpeculativeCtorMarker.class);

	public static final Type SPECULATIVECTORMARKER =
		Type.fromClass(jaspex.speculation.runtime.SpeculativeCtorMarker.class);

	public static final Type SPECULATIONCONTROL =
		Type.fromClass(jaspex.speculation.runtime.SpeculationControl.class);

	public static final Type CONTSPECULATIONCONTROL =
		Type.fromClass(jaspex.speculation.nsruntime.ContSpeculationControl.class);

	public static final Type TRANSACTIONAL =
		Type.fromClass(jaspex.speculation.runtime.Transactional.class);

	public static final Type FUTURE =
		Type.fromClass(java.util.concurrent.Future.class);

	public static final Type CALLABLE =
		Type.fromClass(jaspex.speculation.runtime.Callable.class);

	public static final Type PREDICTABLECALLABLE =
		Type.fromClass(jaspex.speculation.nsruntime.PredictableCallable.class);

	public static final Type PREDICTORFACTORY =
		Type.fromClass(jaspex.speculation.nsruntime.PredictorFactory.class);

	public static final Type PREDICTOR =
		Type.fromClass(jaspex.speculation.nsruntime.Predictor.class);

	public static final Type TRANSACTION =
		Type.fromClass(jaspex.stm.Transaction.class);

	public static final Type STATICFIELDBASE =
		Type.fromClass(jaspex.stm.StaticFieldBase.class);

	public static final Type DEBUGCLASS =
		Type.fromClass(jaspex.Debug.class);

	public static final Type REPLACEMENTS =
		Type.fromClass(jaspex.speculation.Replacements.class);

	public static final String MARKER_BEFOREINLINEDSTORE = "jaspex/MARKER/beforeInlinedStore";

}
