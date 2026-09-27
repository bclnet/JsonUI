// GENERATED FILE - do not edit. Source: scripts/jsonui-prelude.js (run scripts/sync-prelude.py).
package com.bclnet.jsonui

/** The JavaScript runtime installed into every script engine before the document script. */
object ScriptPrelude {
    const val SOURCE: String = """// JsonUI script prelude.
//
// This file is the single source of truth for the JavaScript runtime that
// JsonUI installs into the script engine (JavaScriptCore on Apple platforms,
// QuickJS on Android). Copies are embedded in
//   ios/Sources/JsonUICore/ScriptPrelude.swift
//   android/jsonui-core/src/main/kotlin/com/bclnet/jsonui/ScriptPrelude.kt
// and regenerated with `python3 scripts/sync-prelude.py`.
//
// The host installs a global `__jsonui_host` object before this prelude runs.
// Every bridge method exchanges JSON strings so that the same prelude works on
// engines that only marshal primitives across the bridge:
//   get(path) -> json        value at a dotted/indexed state path ("null" when absent)
//   set(path, json)          assign a value at a state path
//   has(key) -> bool         whether a top-level key exists
//   keys() -> json           array of top-level keys
//   snapshot() -> json       the whole state object
//   merge(json)              shallow-merge an object into the state
//   invoke(name, json) -> json   call a host action; result as JSON ("null" when none)
//   log(level, message)      forward console output
(function (global) {
  'use strict';
  var host = global.__jsonui_host;
  if (!host) { throw new Error('JsonUI: __jsonui_host is not installed'); }

  function parse(json) {
    if (json === undefined || json === null || json === '') { return null; }
    if (typeof json !== 'string') { return json; }
    try { return JSON.parse(json); } catch (e) { return null; }
  }
  function stringify(value) {
    if (value === undefined) { return 'null'; }
    var json = JSON.stringify(value);
    return json === undefined ? 'null' : json;
  }

  var stateProxy = new Proxy({}, {
    get: function (_, key) {
      if (typeof key !== 'string') { return undefined; }
      if (key === 'toJSON') { return function () { return parse(host.snapshot()); }; }
      return parse(host.get(key));
    },
    set: function (_, key, value) { host.set(String(key), stringify(value)); return true; },
    has: function (_, key) { return !!host.has(String(key)); },
    deleteProperty: function (_, key) { host.set(String(key), 'null'); return true; },
    ownKeys: function () { return parse(host.keys()) || []; },
    getOwnPropertyDescriptor: function (_, key) {
      if (!host.has(String(key))) { return undefined; }
      return { enumerable: true, configurable: true, writable: true, value: parse(host.get(String(key))) };
    }
  });

  var console = global.console || {};
  function logger(level) {
    return function () {
      var parts = [];
      for (var i = 0; i < arguments.length; i++) {
        var a = arguments[i];
        parts.push(typeof a === 'string' ? a : stringify(a));
      }
      host.log(level, parts.join(' '));
    };
  }
  console.log = logger('log');
  console.info = logger('info');
  console.warn = logger('warn');
  console.error = logger('error');
  console.debug = logger('debug');

  global.console = console;
  global.state = stateProxy;
  global.getState = function () { return parse(host.snapshot()); };
  global.setState = function (patch) { host.merge(stringify(patch || {})); };
  global.host = {
    invoke: function (name, args) { return parse(host.invoke(String(name), stringify(args === undefined ? null : args))); }
  };
  global.jsonui = { version: '1.0.0' };

  // Evaluates `expression` with the given locals (a JSON object) in scope and
  // returns the result as JSON. Used for "${'$'}{...}" property values.
  global.__jsonui_eval = function (expression, localsJson) {
    var locals = parse(localsJson) || {};
    var names = Object.keys(locals);
    var values = names.map(function (n) { return locals[n]; });
    var fn = Function.apply(null, names.concat(['return (' + expression + '\n);']));
    return stringify(fn.apply(null, values));
  };

  // Runs `script` (statements) with the given locals in scope.
  global.__jsonui_run = function (script, localsJson) {
    var locals = parse(localsJson) || {};
    var names = Object.keys(locals);
    var values = names.map(function (n) { return locals[n]; });
    var fn = Function.apply(null, names.concat([script]));
    return stringify(fn.apply(null, values));
  };
})(typeof globalThis !== 'undefined' ? globalThis : this);
"""
}
