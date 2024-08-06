<script>
  import SkNode from "./lib/SkNode.svelte";
  import { onMount, setContext } from "svelte";
  import { writable } from "svelte/store";
  import { load, postApi } from "./lib/common.js";
  import SkButton from "./lib/SkButton.svelte";

  let nodes = writable(new Map());

  let loaded = false;

  setContext("nodes", nodes);

  onMount(async () => {
    let response = await load();
    let m = new Map();

    response.forEach((node) => {
      // Temporary: this "expands" each node in the temporary linear UI to the first child
      // of that node.
      node.selectedId = node.children[0];
      m.set(node.id, node);
    });

    nodes.set(m);

    loaded = true;
  });

  function processResult(event) {
    console.debug(event);

    const response = event.detail.response;

    response.updates.forEach((n) => nodes.update((m) => m.set(n.id, n)));

    // TODO: Deletes and anything else we want to support (timing, status message, etc.).

  }

  async function save() {
    let response = await  postApi({ action: "save" });

    processResult(response);

  }
</script>

<div class="container mx-lg mx-auto px-8 py-4">
  <div class="flex flex-row mb-8">
    <div class="text-emerald-600 text-3xl">Dialog Skein</div>
    <SkButton on:click={save}>Save</SkButton>
  </div>

  {#if loaded}
    <SkNode id={0} on:response={ processResult }/>
  {/if}
</div>

