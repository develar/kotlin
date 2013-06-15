// Be aware — Google Chrome has serious issue — you can rewrite READ-ONLY property (if it is defined in prototype). Firefox and Safari work correct.
// Always test property access issues in Firefox, but not in Chrome.
var Kotlin = Object.create(null, {
    modules: {value: Object.create(null)},
    keys: {value: Object.keys}
});

(function () {
    "use strict";

    Kotlin.isType = function (object, type) {
        if (object === null || object === undefined) {
            return false;
        }

        var proto = Object.getPrototypeOf(object);
        // todo test nested class
        //noinspection RedundantIfStatementJS
        if (proto == type.proto) {
            return true;
        }

        return false;
    };

    // as separated function to reduce scope
    function createConstructor() {
        return function $fun() {
            var o = Object.create($fun.proto);
            var initializer = $fun.initializer;
            if (initializer != null) {
                if (initializer.length == 0) {
                    initializer.call(o);
                }
                else {
                    initializer.apply(o, arguments);
                }
            }
            return o;
        };
    }

    function computeProto(bases, properties) {
        var proto = null;
        for (var i = 0, n = bases.length; i < n; i++) {
            var base = bases[i];
            var baseProto = base.proto;
            if (baseProto === null || base.properties === null) {
                continue;
            }

            if (proto === null) {
                proto = Object.create(baseProto, properties || undefined);
                continue;
            }
            Object.defineProperties(proto, base.properties);
            // todo test A -> B, C(->D) *properties from D is not yet added to proto*
        }

        return proto;
    }

    Kotlin.createClass = function (bases, initializer, properties) {
        // proto must be created for class even if it is not needed (requires for is operator)
        return createClass(bases, initializer === null ? function () {} : initializer, properties, true);
    };

    function computeProto2(bases, properties) {
        if (bases === null) {
            return Object.prototype;
        }
        return Array.isArray(bases) ? computeProto(bases, properties) : bases.proto;
    }

    Kotlin.createObject = function (bases, initializer, properties) {
        var o = Object.create(computeProto2(bases, properties), properties || undefined);
        if (initializer !== null) {
            if (bases !== null) {
                Object.defineProperty(initializer, "baseInitializer", {value: Array.isArray(bases) ? bases[0].initializer : bases.initializer});
            }
            initializer.call(o);
        }
        return o;
    };

    function createClass(bases, initializer, properties, isClass) {
        var proto;
        var baseInitializer;
        if (bases === null) {
            baseInitializer = null;
            proto = !isClass && properties === null ? null : Object.create(null, properties || undefined);
        }
        else if (!Array.isArray(bases)) {
            baseInitializer = bases.initializer;
            proto = !isClass && properties === null ? bases.proto : Object.create(bases.proto, properties || undefined);
        }
        else {
            // first is superclass, other are traits
            baseInitializer = bases[0].initializer;
            proto = computeProto(bases, properties);
            // all bases are traits without properties
            if (proto === null && isClass) {
                proto = Object.create(null, properties || undefined);
            }
        }

        var constructor = createConstructor();
        Object.defineProperty(constructor, "proto", {value: proto});
        Object.defineProperty(constructor, "properties", {value: properties || null});
        if (isClass) {
            Object.defineProperty(constructor, "initializer", {value: initializer});

            Object.defineProperty(initializer, "baseInitializer", {value: baseInitializer});
        }
        return constructor;
    }

    Kotlin.p = function (m, name, initializer, members) {
        var current = name === null ? m : m[name];
        if (current === undefined) {
            // module can contains members for root namespace, so, we need to keep registered package name in separated list
            var packageNames = m.$packageNames$;
            if (packageNames == null) {
                packageNames = [name];
                m.$packageNames$ = packageNames;
            }
            else {
                packageNames.push(name);
            }

            m[name] = {
                $members$: Object.create(null, members === null ? undefined : members),
                $initializers$: initializer === null ? null : initializer
            };
        }
        else {
            if (members !== null) {
                Object.defineProperties(name === null ? m  : current.$members$, members);
            }
            if (initializer !== null) {
                var currentInitializers = current.$initializers$;
                if (currentInitializers === null || currentInitializers === undefined) {
                    current.$initializers$ = initializer;
                }
                else if (Array.isArray(currentInitializers)) {
                    currentInitializers.push(initializer);
                }
                else {
                    current.$initializers$ = [currentInitializers, initializer];
                }
            }
        }
    };

    Kotlin.finalize = function(m) {
        var packageNames = m.$packageNames$;
        if (packageNames !== undefined) {
            delete m.$packageNames$;
            for (var i = 0, n = packageNames.length; i < n; i++) {
                var name = packageNames[i];
                var p = m[name];
                var initializers = p.$initializers$;
                if (initializers == null) {
                    m[name] = p.$members$;
                }
                else {
                    var getter = createPackageGetter(p.$members$, initializers);
                    Object.defineProperty(m, name, {get: getter});
                }
            }
        }

        var rootInitializers = m.$initializers$;
        // pending initialization is not supported for root package
        if (rootInitializers !== undefined) {
            delete m.$initializers$;
            invokeInitializers(rootInitializers, m);
        }
    };

    function invokeInitializers(tmp, instance) {
        if (Array.isArray(tmp)) {
            for (var i = 0, n = tmp.length; i < n; i++) {
                tmp[i].call(instance);
            }
        }
        else {
            tmp.call(instance);
        }
    }

    function createPackageGetter(instance, initializers) {
        return function () {
            if (initializers !== null) {
                var tmp = initializers;
                initializers = null;
                invokeInitializers(tmp, instance);
            }
            return instance;
        };
    }

    Kotlin.$new = function (f) {
        return f;
    };

    Kotlin.$createClass = function (parent, properties) {
        if (parent !== null && typeof (parent) != "function") {
            properties = parent;
            parent = null;
        }

        var initializer = null;
        var descriptors = properties ? {} : null;
        if (descriptors != null) {
            var ownPropertyNames = Object.getOwnPropertyNames(properties);
            for (var i = 0, n = ownPropertyNames.length; i < n; i++) {
                var name = ownPropertyNames[i];
                var value = properties[name];
                if (name == "initialize") {
                    initializer = value;
                }
                else if (name.indexOf("get_") === 0) {
                    descriptors[name.substring(4)] = {get: value};
                    // std lib code can refers to
                    descriptors[name] = {value: value};
                }
                else if (name.indexOf("set_") === 0) {
                    descriptors[name.substring(4)] = {set: value};
                    // std lib code can refers to
                    descriptors[name] = {value: value};
                }
                else {
                    // we assume all our std lib functions are open
                    descriptors[name] = {value: value, writable: true};
                }
            }
        }

        return Kotlin.createClass(parent || null, initializer, descriptors);
    };

    Kotlin.doDefineModule = function (id, declaration) {
        Kotlin.modules[id] = declaration;
    };
})();
