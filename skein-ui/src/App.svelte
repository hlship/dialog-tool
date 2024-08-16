<script>
  import Knot from "./lib/Knot.svelte";
  import { onMount, setContext } from "svelte";
  import { writable } from "svelte/store";
  import { load, postApi } from "./lib/common.js";
  import SkButton from "./lib/SkButton.svelte";

  let knots = writable(new Map());

  let loaded = false;

  let enableUndo = false;
  let enableRedo = false;

  setContext("knots", knots);

  onMount(async () => {
    let response = await load();

    knots.update((m) => {

      response.forEach((node) => {
        // Temporary: this "expands" each node in the temporary linear UI to the first child
        // of that node.
        let knot = writable({ node: node, selectedId: node.children[0] });
        m.set(node.id, knot);
      });

      return m;
    });

    loaded = true;
  });

  function processResult(result) {
    knots.update(m => {
      console.debug(m);
      result.updates.forEach((n) => {
        let knot = m.get(n.id) || writable({});
        knot.node = n;
      });

      // For each deleted node, we need to find the node's parent and ensure that it is not
      // the selected child.

      return m;
    });

    enableUndo = result.enable_undo;
    enableRedo = result.enable_redo;
  }

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
