<script>
  import Knot from "./lib/Knot.svelte";
  import { onMount, setContext } from "svelte";
  import { writable } from "svelte/store";
  import { load, postApi } from "./lib/common.js";
  import SkButton from "./lib/SkButton.svelte";

  let knots = writable(new Map());
  let childNames = writable(new Map());

  let loaded = false;

  let enableUndo = false;
  let enableRedo = false;

  setContext("knots", knots);
  setContext("childNames", childNames);

  function processResult(result) {
    knots.update(m => {
      result.updates.forEach(n => {
        let knot = m.get(n.id) || {};
        if (knot.node) {
          knot.node.set(n);
        }
        else {
          // A new node needs to be wrapped in a writable, added to the map
          knot.node = writable(n);
          m.set(n.id, knot);

          knot.children = []; // TEMPORARY!
        } 

        if (!loaded) {
          knot.selectedId = n.children[0];
        }
      });
      // TODO: For each deleted node, we need to find the node's parent and ensure that it is not
      // the selected child.

      return m;
    });

    childNames.update(m => {

      result.updates.forEach(n => m.set(n.id, n.command));

      return m;
    });

    enableUndo = result.enable_undo;
    enableRedo = result.enable_redo;
  }

  onMount(async () => {
    let result = await load();

    processResult(result);

    loaded = true;
  });

  async function doPost(request) {
    let result = await postApi(request);

    processResult(result);
  }

  function save() {
    doPost({ action: "save" });
  }

  function undo() {
    doPost({ action: "undo" });
  }

  function redo() {
    doPost({ action: "redo" });
  }
</script>

<div class="container mx-lg mx-auto px-8 py-4">
  <div class="flex flex-row mb-8">
    <div class="text-emerald-600 text-3xl">Dialog Skein</div>
    <SkButton on:click={save}>Save</SkButton>
    <SkButton on:click={undo} disabled={!enableUndo}>Undo</SkButton>
    <SkButton on:click={redo} disabled={!enableRedo}>Redo</SkButton>
  </div>

  {#if loaded}
    <Knot id={0} on:result={(event) => processResult(event.detail)} />
  {/if}
</div>
