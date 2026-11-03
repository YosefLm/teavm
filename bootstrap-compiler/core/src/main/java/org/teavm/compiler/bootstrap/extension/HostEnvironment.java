/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.compiler.bootstrap.extension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.teavm.extension.ExtensionEnvironmentImpl;
import org.teavm.extension.bridge.BridgeClass;
import org.teavm.extension.bridge.BridgeElement;
import org.teavm.extension.bridge.BridgeHost;
import org.teavm.extension.bridge.BridgeMethod;
import org.teavm.extension.bridge.BridgeResource;
import org.teavm.extension.diagnostics.SourceLocation;
import org.teavm.extension.introspect.IntrospectAnnotation;
import org.teavm.extension.introspect.IntrospectClass;
import org.teavm.extension.introspect.IntrospectField;
import org.teavm.extension.introspect.IntrospectGenericArray;
import org.teavm.extension.introspect.IntrospectMethod;
import org.teavm.extension.introspect.IntrospectParameter;
import org.teavm.extension.introspect.IntrospectParameterizedType;
import org.teavm.extension.introspect.IntrospectType;
import org.teavm.extension.introspect.IntrospectTypeArgument;
import org.teavm.extension.introspect.IntrospectTypeVariable;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.model.ValueType;

/**
 * Exposes {@link ExtensionEnvironmentImpl} to guest. Every introspected element is represented by exactly one
 * host element, so guest observes the same JS object for the same element.
 */
public class HostEnvironment implements BridgeHost {
    final ExtensionEnvironmentImpl env;
    private Map<Object, HostElement> elements = new IdentityHashMap<>();

    public HostEnvironment(ExtensionEnvironmentImpl env) {
        this.env = env;
    }

    @Override
    public BridgeClass findClass(String name) {
        return cls(resolveClass(name));
    }

    private IntrospectClass<?> resolveClass(String name) {
        switch (name) {
            case "boolean":
                return env.introspection().getClass(ValueType.BOOLEAN);
            case "byte":
                return env.introspection().getClass(ValueType.BYTE);
            case "short":
                return env.introspection().getClass(ValueType.SHORT);
            case "char":
                return env.introspection().getClass(ValueType.CHARACTER);
            case "int":
                return env.introspection().getClass(ValueType.INTEGER);
            case "long":
                return env.introspection().getClass(ValueType.LONG);
            case "float":
                return env.introspection().getClass(ValueType.FLOAT);
            case "double":
                return env.introspection().getClass(ValueType.DOUBLE);
            case "void":
                return env.introspection().getClass(ValueType.VOID);
        }
        if (name.startsWith("[")) {
            var type = ValueType.parseIfPossible(name.replace('.', '/'));
            return type != null ? env.introspection().getClass(type) : null;
        }
        return env.findClass(name);
    }

    @Override
    public String property(String name) {
        return env.property(name);
    }

    @Override
    public BridgeResource[] resources(String name) {
        var result = new ArrayList<HostResource>();
        var iterator = env.resources(name);
        while (iterator.hasNext()) {
            result.add(new HostResource(iterator.next()));
        }
        return result.toArray(new HostResource[0]);
    }

    @Override
    public void report(boolean error, BridgeMethod method, String fileName, int lineNumber, String message,
            BridgeElement[] paramElements, String[] paramStrings) {
        var params = new Object[paramElements.length];
        for (var i = 0; i < params.length; ++i) {
            params[i] = paramElements[i] != null ? ((HostElement) paramElements[i]).underlying() : paramStrings[i];
        }
        SourceLocation location = null;
        if (method != null) {
            var introspectMethod = ((HostMethod) method).method;
            location = fileName != null
                    ? new SourceLocation(introspectMethod, fileName, lineNumber)
                    : new SourceLocation(introspectMethod);
        }
        if (error) {
            env.diagnostics().error(location, message, params);
        } else {
            env.diagnostics().warning(location, message, params);
        }
    }

    HostClass cls(IntrospectClass<?> cls) {
        if (cls == null) {
            return null;
        }
        var result = (HostClass) elements.get(cls);
        if (result == null) {
            result = new HostClass(this, cls);
            elements.put(cls, result);
        }
        return result;
    }

    HostField field(IntrospectField field) {
        if (field == null) {
            return null;
        }
        var result = (HostField) elements.get(field);
        if (result == null) {
            result = new HostField(this, field);
            elements.put(field, result);
        }
        return result;
    }

    HostMethod method(IntrospectMethod method) {
        if (method == null) {
            return null;
        }
        var result = (HostMethod) elements.get(method);
        if (result == null) {
            result = new HostMethod(this, method);
            elements.put(method, result);
        }
        return result;
    }

    HostParameter parameter(IntrospectParameter parameter) {
        var result = (HostParameter) elements.get(parameter);
        if (result == null) {
            result = new HostParameter(this, parameter);
            elements.put(parameter, result);
        }
        return result;
    }

    HostAnnotation annotation(IntrospectAnnotation<?> annotation) {
        if (annotation == null) {
            return null;
        }
        var result = (HostAnnotation) elements.get(annotation);
        if (result == null) {
            result = new HostAnnotation(this, annotation);
            elements.put(annotation, result);
        }
        return result;
    }

    HostTypeArgument typeArgument(IntrospectTypeArgument argument) {
        var result = (HostTypeArgument) elements.get(argument);
        if (result == null) {
            result = new HostTypeArgument(this, argument);
            elements.put(argument, result);
        }
        return result;
    }

    HostType type(IntrospectType type) {
        if (type == null) {
            return null;
        }
        if (type instanceof IntrospectClass<?>) {
            return cls((IntrospectClass<?>) type);
        }
        var result = (HostType) elements.get(type);
        if (result == null) {
            if (type instanceof IntrospectTypeVariable) {
                result = new HostTypeVariable(this, (IntrospectTypeVariable) type);
            } else if (type instanceof IntrospectParameterizedType) {
                result = new HostParameterizedType(this, (IntrospectParameterizedType) type);
            } else if (type instanceof IntrospectGenericArray) {
                result = new HostGenericArray(this, (IntrospectGenericArray) type);
            } else {
                throw new IllegalArgumentException("Unsupported type: " + type);
            }
            elements.put(type, result);
        }
        return result;
    }

    HostClass[] classes(List<? extends IntrospectClass<?>> classes) {
        var result = new HostClass[classes.size()];
        for (var i = 0; i < result.length; ++i) {
            result[i] = cls(classes.get(i));
        }
        return result;
    }

    HostField[] fields(List<? extends IntrospectField> fields) {
        var result = new HostField[fields.size()];
        for (var i = 0; i < result.length; ++i) {
            result[i] = field(fields.get(i));
        }
        return result;
    }

    HostMethod[] methods(List<? extends IntrospectMethod> methods) {
        var result = new HostMethod[methods.size()];
        for (var i = 0; i < result.length; ++i) {
            result[i] = method(methods.get(i));
        }
        return result;
    }

    HostTypeVariable[] typeVariables(List<? extends IntrospectTypeVariable> variables) {
        var result = new HostTypeVariable[variables.size()];
        for (var i = 0; i < result.length; ++i) {
            result[i] = (HostTypeVariable) type(variables.get(i));
        }
        return result;
    }

    HostType[] types(List<? extends IntrospectType> types) {
        var result = new HostType[types.size()];
        for (var i = 0; i < result.length; ++i) {
            result[i] = type(types.get(i));
        }
        return result;
    }

    IntrospectClass<?> unwrapClass(BridgeClass cls) {
        return cls != null ? ((HostClass) cls).cls : null;
    }

    IntrospectClass<?>[] unwrapClasses(BridgeClass[] classes) {
        var result = new IntrospectClass<?>[classes.length];
        for (var i = 0; i < classes.length; ++i) {
            result[i] = unwrapClass(classes[i]);
        }
        return result;
    }

    private static class HostResource implements BridgeResource {
        private org.teavm.extension.resource.Resource resource;

        HostResource(org.teavm.extension.resource.Resource resource) {
            this.resource = resource;
        }

        @Override
        public Int8Array read() {
            try (var input = resource.open()) {
                var output = new ByteArrayOutputStream();
                input.transferTo(output);
                return Int8Array.copyFromJavaArray(output.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
