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

package jaspex.util;

import java.lang.reflect.Field;

/**
 * Simple class to obtain access to the {@link Unsafe} object.
 */
public final class Unsafe {
    public static final sun.misc.Unsafe UNSAFE = getUnsafe();

    @SuppressWarnings("unchecked")
    private static <UNSAFE> UNSAFE getUnsafe() {
        Object theUnsafe = null;
        Exception exception = null;

        try {
            Class<?> uc = Class.forName("sun.misc.Unsafe");
            Field f = uc.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            theUnsafe = f.get(uc);
        } catch (Exception e) { exception = e; }

        if (theUnsafe == null) throw new Error("Could not obtain access to sun.misc.Unsafe", exception);
        return (UNSAFE) theUnsafe;
    }

    private Unsafe() { }
}
