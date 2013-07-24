"use strict";

var kotlin = {
    "isType": function (object, type) {
        if (object instanceof type) {
            return true;
        }
        if (object == null) {
            return false;
        }
        var proto = object.__proto__;
        if (proto === undefined) {
            return false
        }
        var objectType = proto.constructor;
        return objectType === type ||
               (objectType !== undefined && objectType.superTypes$ !== null && objectType.superTypes$.indexOf(type) !== -1);
    },
    newException: function (message, name) {
        var error = new Error(message);
        error.name = name;
        return error;
    },
    defineModule: function (id, moduleDependencies, definitionFunction) {
        if (id in kotlin.modules) {
            throw new Error("Module " + id + " is already defined");
        }
        kotlin.modules[id] = definitionFunction();
    },
    assignOwner: function (f, o) {
        f.o = o;
        return f;
    },
    throwNPE: function () {
        var error = new ReferenceError();
        error.name = "NullPointerException";
        throw error;
    },
    equals: function (obj1, obj2) {
        if (obj1 === obj2) {
            return true;
        }
        else if (obj1 == null || obj2 == null) {
            return false;
        }
        else if (Array.isArray(obj1)) {
            return kotlin.arrayEquals(obj1, obj2);
        }

        var o1Type = typeof obj1;
        if (o1Type === "object" && "equals" in obj1) {
            return obj1.equals(obj2);
        }
        var o2Type = typeof obj2;
        if (o2Type === "object" && "equals" in obj2) {
            return obj2.equals(obj1);
        }
        else {
            // new String("dd") == "dd"
            return o1Type === o2Type && obj1 == obj2;
        }
    },
    arrayEquals: function (a, b) {
        if (a === b) {
            return true;
        }
        if (!Array.isArray(b) || a.length !== b.length) {
            return false;
        }

        for (var i = 0, n = a.length; i < n; i++) {
            if (!kotlin.equals(a[i], b[i])) {
                return false;
            }
        }
        return true;
    },
    arrayFromFun: function (size, initFun) {
        var result = new Array(size);
        for (var i = 0; i < size; i++) {
            result[i] = initFun(i);
        }
        return result;
    },
    stringify: function (o) {
        if (o === null || o === undefined) {
            return "null";
        }
        else if (Array.isArray(o)) {
            return kotlin.arrayToString(o);
        }
        else {
            return o.toString();
        }
    },
    arrayToString: function (a) {
        return "[" + a.join(", ") + "]";
    },
    arrayIndexOf: function (a, o) {
        var i = 0, n = a.length;
        if (o == null) {
            for (; i < n; i++) {
                if (a[i] == null) {
                    return i;
                }
            }
        }
        else {
            for (; i < n; i++) {
                if (kotlin.equals(a[i], o)) {
                    return i;
                }
            }
        }
        return -1;
    },
    arrayLastIndexOf: function (a, o) {
        var i = a.length - 1;
        if (o == null) {
            for (; i > -1; i--) {
                if (a[i] == null) {
                    return i;
                }
            }
        }
        else {
            for (; i > -1; i--) {
                if (kotlin.equals(a[i], o)) {
                    return i;
                }
            }
        }
        return -1;
    },
    arrayRemove: function (a, o) {
        var index = kotlin.arrayIndexOf(a, o);
        if (index !== -1) {
            a.splice(index, 1);
            return true;
        }
        return false;
    },
    arrayAddAll: function (a, collection) {
        var i, n;
        if (Array.isArray(collection)) {
            var j = 0;
            for (i = a.length, n = collection.length; n-- > 0;) {
                a[i++] = collection[j++];
            }
            return j > 0;
        }

        var it = collection.iterator();
        // http://jsperf.com/arrays-push-vs-index
        for (i = a.length, n = collection.size(); n-- > 0;) {
            a[i++] = it.next();
        }
        return collection.size() != 0
    },
    arrayAddAt: function (a, index, o) {
        if (index > a.length || index < 0) {
            throw new RangeError("Index: " + index + ", Size: " + a.length);
        }
        return a.splice(index, 0, o);
    },
    collectionAdd: function (c, o) {
        return Array.isArray(c) ? c.push(o) : c.add(o);
    },
    collectionAddAll: function (c, collection) {
        if (Array.isArray(c)) {
            return kotlin.arrayAddAll(c, collection);
        }

        return c.addAll(collection);
    },
    collectionRemove: function (c, o) {
        return Array.isArray(c) ? Kotlin.arrayRemove(c, o) : c.remove(o);
    },
    collectionClear: function (c) {
        if (Array.isArray(c)) {
            c.length = 0;
        }
        else {
            c.clear();
        }
    },
    collectionIterator: function (c) {
        return Array.isArray(c) ? kotlin.modules['js-stdlib'].js_stdlib.arrayIterator(c) : c.iterator();
    },
    collectionSize: function (c) {
        return Array.isArray(c) ? c.length : c.size();
    },
    collectionIsEmpty: function (c) {
        return Array.isArray(c) ? c.length === 0 : c.isEmpty();
    },
    collectionContains: function (c, o) {
        return Array.isArray(c) ? kotlin.arrayIndexOf(c, o) !== -1 : c.contains(o);
    },
    collectionsMax: function (c, comparator) {
        if (kotlin.collectionIsEmpty(c)) {
            //TODO: which exception?
            throw new Error();
        }

        var it = kotlin.collectionIterator(c);
        var max = it.next();
        while (it.hasNext()) {
            var el = it.next();
            if (comparator.compare(max, el) < 0) {
                max = el;
            }
        }
        return max;
    },
    modules: {}
};

(function () {
    Kotlin.Collection = Kotlin.$createClass();

    Kotlin.AbstractCollection = Kotlin.$createClass(Kotlin.Collection, {
        size: function () {
            return this.$size;
        },
        addAll: function (collection) {
            var it = collection.iterator();
            var i = this.size();
            while (i-- > 0) {
                this.add(it.next());
            }
        },
        isEmpty: function () {
            return this.size() === 0;
        },
        iterator: function () {
            return Kotlin.$new(ArrayIterator)(this.toArray());
        },
        equals: function (o) {
            if (this.size() === o.size()) {
                var iterator1 = this.iterator();
                var iterator2 = o.iterator();
                var i = this.size();
                while (i-- > 0) {
                    if (!kotlin.equals(iterator1.next(), iterator2.next())) {
                        return false;
                    }
                }
            }
            return true;
        },
        toString: function () {
            var builder = "[";
            var iterator = this.iterator();
            var first = true;
            var i = this.$size;
            while (i-- > 0) {
                if (first) {
                    first = false;
                }
                else {
                    builder += ", ";
                }
                builder += iterator.next();
            }
            builder += "]";
            return builder;
        },
        toJSON: function () {
            return this.toArray();
        }
    });

    Kotlin.safeParseInt = function(str) {
        var r = parseInt(str, 10);
        return isNaN(r) ? null : r;
    };

    Kotlin.safeParseDouble = function(str) {
        var r = parseFloat(str);
        return isNaN(r) ? null : r;
    };

    function rangeCheck(index, n) {
        if (index < 0 || index >= n) {
            throw new RangeError("Index: " + index + ", Size: " + n);
        }
    }
    Kotlin.arrayGet = function (a, index) {
        rangeCheck(index, a.length);
        return a[index];
    };
    Kotlin.arraySet = function (a, index, o) {
        rangeCheck(index, a.length);
        a[index] = o;
        return true;
    };
    Kotlin.arrayRemoveAt = function (a, index) {
        rangeCheck(index, a.length);
        return a.splice(index, 1)[0];
    };

    Kotlin.System = function () {
        var output = "";
        return {
            out: {
                print: function (obj) {
                    if (obj !== undefined) {
                        if (obj === null || typeof obj !== "object") {
                            output += obj;
                        }
                        else {
                            output += obj.toString();
                        }
                    }
                },
                println: function (obj) {
                    this.print(obj);
                    output += "\n";
                }
            },
            output: function () {
                return output;
            },
            flush: function () {
                output = "";
            }
        };
    }();

    function nullFun(i) {
        return null;
    }

    Kotlin.arrayOfNulls = function (size) {
        return kotlin.arrayFromFun(size, nullFun);
    };

    Kotlin.jsonFromTuples = function (pairArr) {
        var i = pairArr.length;
        var res = {};
        while (i > 0) {
            --i;
            res[pairArr[i][0]] = pairArr[i][1];
        }
        return res;
    };

    Kotlin.jsonAddProperties = function (obj1, obj2) {
        for (var p in obj2) {
            if (obj2.hasOwnProperty(p)) {
                obj1[p] = obj2[p];
            }
        }
        return obj1;
    };
})();