// Exercises jsonui-prelude.js under Node with a fake host bridge.
// Run: node scripts/test-prelude.js
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const vm = require('vm');

const prelude = fs.readFileSync(path.join(__dirname, 'jsonui-prelude.js'), 'utf8');

function makeHost() {
  const state = {};
  const calls = [];
  const logs = [];
  function getPath(obj, p) {
    const parts = p.replace(/\[(\d+)\]/g, '.$1').split('.').filter(Boolean);
    let cur = obj;
    for (const part of parts) { if (cur == null) return undefined; cur = cur[part]; }
    return cur;
  }
  function setPath(obj, p, v) {
    const parts = p.replace(/\[(\d+)\]/g, '.$1').split('.').filter(Boolean);
    let cur = obj;
    for (let i = 0; i < parts.length - 1; i++) {
      if (cur[parts[i]] == null) cur[parts[i]] = /^\d+$/.test(parts[i + 1]) ? [] : {};
      cur = cur[parts[i]];
    }
    cur[parts[parts.length - 1]] = v;
  }
  return {
    state, calls, logs,
    bridge: {
      get: (p) => JSON.stringify(getPath(state, p) === undefined ? null : getPath(state, p)),
      set: (p, json) => setPath(state, p, JSON.parse(json)),
      has: (k) => Object.prototype.hasOwnProperty.call(state, k),
      keys: () => JSON.stringify(Object.keys(state)),
      snapshot: () => JSON.stringify(state),
      merge: (json) => Object.assign(state, JSON.parse(json)),
      invoke: (name, json) => { calls.push([name, JSON.parse(json)]); return JSON.stringify({ ok: name }); },
      log: (level, msg) => logs.push([level, msg]),
    }
  };
}

const host = makeHost();
host.state.email = 'a@b.co';
host.state.items = [{ n: 1 }, { n: 2 }];
const ctx = vm.createContext({ __jsonui_host: host.bridge });
vm.runInContext(prelude, ctx);

// state proxy read/write
assert.strictEqual(vm.runInContext('state.email', ctx), 'a@b.co');
vm.runInContext("state.email = 'x@y.z'", ctx);
assert.strictEqual(host.state.email, 'x@y.z');
assert.strictEqual(vm.runInContext("'email' in state", ctx), true);
assert.strictEqual(vm.runInContext('JSON.stringify(Object.keys(state))', ctx), '["email","items"]');
assert.deepStrictEqual(JSON.parse(vm.runInContext('JSON.stringify(state)', ctx)), host.state);

// eval with locals
assert.strictEqual(vm.runInContext('__jsonui_eval("item.n * 10", JSON.stringify({item: {n: 4}}))', ctx), '40');
assert.strictEqual(vm.runInContext('__jsonui_eval("state.items.length", "{}")', ctx), '2');
assert.strictEqual(vm.runInContext('__jsonui_eval("undefined", "{}")', ctx), 'null');

// run with locals and host invoke
vm.runInContext('__jsonui_run("state.count = (state.count || 0) + inc; host.invoke(\'tick\', {by: inc})", JSON.stringify({inc: 5}))', ctx);
assert.strictEqual(host.state.count, 5);
assert.deepStrictEqual(host.calls, [['tick', { by: 5 }]]);
assert.deepStrictEqual(JSON.parse(vm.runInContext("JSON.stringify(host.invoke('x', 1))", ctx)), { ok: 'x' });

// setState / getState / console
vm.runInContext("setState({flag: true}); console.log('hello', {a: 1})", ctx);
assert.strictEqual(host.state.flag, true);
assert.deepStrictEqual(vm.runInContext('getState().flag', ctx), true);
assert.deepStrictEqual(host.logs, [['log', 'hello {"a":1}']]);

// functions defined by a document script are visible to eval
vm.runInContext("function twice(x) { return x * 2; }", ctx);
assert.strictEqual(vm.runInContext('__jsonui_eval("twice(21)", "{}")', ctx), '42');

console.log('prelude tests passed');
