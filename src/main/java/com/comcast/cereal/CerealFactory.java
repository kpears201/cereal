/**
 * Copyright 2012 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.comcast.cereal;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.comcast.cereal.annotations.CerealClass;
import com.comcast.cereal.convert.ArrayCerealizer;
import com.comcast.cereal.convert.ByteArrayCerealizer;
import com.comcast.cereal.convert.CerealizableCerealizer;
import com.comcast.cereal.convert.ClassCerealizer;
import com.comcast.cereal.convert.CollectionCerealizer;
import com.comcast.cereal.convert.DateCerealizer;
import com.comcast.cereal.convert.DynamicCerealizer;
import com.comcast.cereal.convert.EnumCerealizer;
import com.comcast.cereal.convert.MapCerealizer;
import com.comcast.cereal.convert.SimpleCerealizer;
import com.comcast.cereal.convert.PrimitiveCerealizer.BooleanCerealizer;
import com.comcast.cereal.convert.PrimitiveCerealizer.CharCerealizer;
import com.comcast.cereal.convert.PrimitiveCerealizer.DoubleCerealizer;
import com.comcast.cereal.convert.PrimitiveCerealizer.FloatCerealizer;
import com.comcast.cereal.convert.PrimitiveCerealizer.IntegerCerealizer;
import com.comcast.cereal.convert.PrimitiveCerealizer.LongCerealizer;
import com.comcast.cereal.convert.PrimitiveCerealizer.ShortCerealizer;
import com.comcast.cereal.engines.CerealEngine;

/**
 * The <i>CerealFactory</i> is a central repository for all {@link Cerealizer} objects within a
 * given {@link CerealEngine}.
 * 
 * @see CerealEngine#getCerealFactory()
 * 
 * @author <a href="mailto:cmalmgren@gmail.com">Clark Malmgren</a>
 */
public class CerealFactory {

    /* This is a mapping of types to cached base type and class cerealizers */
    private final Map<Class<?>, Cerealizer<?, ?>> map;

    /* This is a cache of instance objects for typed cerealizers */
    private final Map<Class<?>, Cerealizer<?, ?>> cache;
    
    /* This is a cache of the Dynamic Cerealizer */
    private DynamicCerealizer dc;

    /**
     * Construct a new {@link CerealFactory} and initialize the types supported by default (see
     * {@link Cerealizer} for that list) to use a {@link SimpleCerealizer}.
     * 
     * @param objectCache
     *            the {@link ObjectCache} used for the {@link CerealEngine} associated with this
     *            factory
     */
    public CerealFactory() {

        this.map = new HashMap<Class<?>, Cerealizer<?, ?>>();
        this.cache = new HashMap<Class<?>, Cerealizer<?, ?>>();

        /* Insert the SimpleCeralizer for all the primitive types */
        final SimpleCerealizer sc = new SimpleCerealizer();
        this.map.put(String.class, sc);
        final BooleanCerealizer bc = new BooleanCerealizer();
        this.map.put(boolean.class, bc);
        this.map.put(Boolean.class, bc);
        this.map.put(byte.class, sc);
        this.map.put(Byte.class, sc);
        final CharCerealizer cs = new CharCerealizer();
        this.map.put(char.class, cs);
        this.map.put(Character.class, cs);
        final ShortCerealizer shc = new ShortCerealizer();
        this.map.put(short.class, shc);
        this.map.put(Short.class, shc);
        final IntegerCerealizer ic = new IntegerCerealizer();
        this.map.put(int.class, ic);
        this.map.put(Integer.class, ic);
        final LongCerealizer lc = new LongCerealizer();
        this.map.put(long.class, lc);
        this.map.put(Long.class, lc);
        final FloatCerealizer fc = new FloatCerealizer();
        this.map.put(float.class, fc);
        this.map.put(Float.class, fc);
        final DoubleCerealizer dbc = new DoubleCerealizer();
        this.map.put(double.class, dbc);
        this.map.put(Double.class, dbc);

        /* If it is java.lang.Object, it should use the DynamicCerealizer */
        dc = new DynamicCerealizer();
        dc.setCerealFactory(this);
        this.map.put(Object.class, dc);

        /* Add the DateCerealizer */
        final DateCerealizer dac = new DateCerealizer();
        this.map.put(Date.class, dac);

        /* Cache the default cerealizers */
        this.cacheCerealizers(sc, bc, cs, shc, ic, lc, fc, dbc, dc, dac);

        /* Simply cache a ByteArrayCerealizer */
        this.cacheCerealizer(new ByteArrayCerealizer());
    }

    /**
     * Get a cached version of a Cerealizer that is capable of converting to and from the specific
     * java type.
     * 
     * @param type
     *            the java type to convert to and from
     * 
     * @return the correct {@link Cerealizer}
     * 
     * @throws CerealException
     *             if there is a problem instantiating the {@link ClassCerealizer} created for the
     *             given type
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <J> Cerealizer<J, ?> getCerealizer(Class<J> type) throws CerealException {
        if (map.containsKey(type)) {
            return (Cerealizer<J, ?>) map.get(type);
        }
        
        /* Handle use case where the class has the @CerealClass annotation */
        CerealClass cerealClass = type.getAnnotation(CerealClass.class);
        if (null != cerealClass) {
            Cerealizer<J, ?> cerealizer = (Cerealizer<J, ?>) getCachedCerealizer(cerealClass.value());
            if (null == cerealizer) {
                try {
                    cerealizer = (Cerealizer<J, ?>) cerealClass.value().newInstance();
                    cacheCerealizer(cerealizer);
                } catch (Exception ex) {
                    throw new CerealException("Failed to create new cerealizer", ex);
                }
            }
            return cerealizer;
        }

        if (type.isEnum()) {
            return new EnumCerealizer(type);
        }
        
        /* Special case to check for a byte array */
        if (type.equals(byte[].class)) {
            return (Cerealizer<J, ?>) getCachedCerealizer(ByteArrayCerealizer.class);
        }

        if (type.isArray()) {
            Class<?> arrayType = type.getComponentType();
            Cerealizer<?, ?> delegate = getCerealizer(arrayType);
            return new ArrayCerealizer(delegate, arrayType);
        }
        
        if (Collection.class.isAssignableFrom(type)) {
            CollectionCerealizer cerealizer = new CollectionCerealizer(dc, (Class<? extends Collection>) type);

            map.put(type, cerealizer);
            cerealizer.setCerealFactory(this);

            return (Cerealizer<J, ?>) cerealizer;
        }
        
        if (Map.class.isAssignableFrom(type)) {
            MapCerealizer mc = new MapCerealizer();
            mc.setMapClass((Class<? extends Map>) type);
            mc.setCerealFactory(this);
            map.put(type, mc);
            return (Cerealizer<J, ?>) mc;
        }

        if (Cerealizable.class.isAssignableFrom(type)) {
            CerealizableCerealizer cerealizer = new CerealizableCerealizer(type);

            map.put(type, cerealizer);
            cerealizer.setCerealFactory(this);

            return cerealizer;
        } else {
            ClassCerealizer<J> cerealizer = new ClassCerealizer<J>(type);

            /*
             * Need to insert the Cerealizer into the map before initializing because
             * self-referencing classes would otherwise infinitely recurse
             */
            map.put(type, cerealizer);

            cerealizer.setCerealFactory(this);
            cerealizer.initialize();

            return cerealizer;
        }
    }
    
    /**
     * Cache an array of cerealizers
     * @param cerealizers The list of cerealizers to cache
     */
    public void cacheCerealizers(Cerealizer<?, ?>... cerealizers) {
    	for (Cerealizer<?, ?> cerealizer : cerealizers) {
    		cacheCerealizer(cerealizer);
    	}
    }

    /**
     * Cache a specific instance of specific cerealizer type. This is primarily used when an
     * implementation of a {@link Cerealizer} requires access to object that must be configured or
     * referenced at runtime.
     * 
     * @param cerealizer
     *            the instance to cache
     * 
     * @see #getCachedCerealizer(Class)
     */
    public void cacheCerealizer(Cerealizer<?, ?> cerealizer) {
        if (cerealizer instanceof CerealFactoryAware) {
            ((CerealFactoryAware) cerealizer).setCerealFactory(this);
        }

        cache.put(cerealizer.getClass(), cerealizer);
    }

    /**
     * Get a cached instance of a specific {@link Cerealizer} if one has already been cached or
     * <code>null</code> if none has been created.
     * 
     * @param type
     *            the java type of {@link Cerealizer} to fetch
     * 
     * @return a cached instance or <code>null</code> if no instance has yet been cached
     * 
     * @see #cacheCerealizer(Cerealizer)
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <C extends Cerealizer> C getCachedCerealizer(Class<C> type) {
        return (C) cache.get(type);
    }
    
    /**
     * Checks if the given cereal is a map and contains a --class property. This property is then used to create
     * a class cerealizer for that class and uses that cerealizer. If the cereal does not have
     * the --class property then the given default cerealizer is used.
     * @param cereal The cereal used to look up the --class property
     * @param defaultCerealizer The cereal to use if the cereal does not have the --class property
     * @return
     * @throws CerealException
     */
    @SuppressWarnings("rawtypes")
    public Cerealizer getRuntimeCerealizer(Object cereal, Cerealizer defaultCerealizer) throws CerealException {
        Class<?> runtimeClass = getRuntimeClass(cereal);
        return runtimeClass == null ? defaultCerealizer : getCerealizer(runtimeClass);
    }

    @SuppressWarnings("rawtypes")
    public Class<?> getRuntimeClass(Object cereal) throws CerealException {
        if (cereal instanceof Map) {
            Map map = (Map) cereal;
            if (map.containsKey("--class")) {
                Object className = map.get("--class");
                if (className instanceof String) {
                    try {
                        return Class.forName((String) className);
                    } catch (ClassNotFoundException e) {
                        throw new CerealException("Failed to get runtime cerealizer", e);
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Tells this factory to use the given cerealizer for the given class
     * @param clazz The class that the cerealizer is for
     * @param cerealizer The cerealizer to use for the given class
     */
    public <T> void addCerealizer(Class<T> clazz, Cerealizer<T, ?> cerealizer) {
    	this.map.put(clazz, cerealizer);
    }

}
