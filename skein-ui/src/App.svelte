<script>
  import SkNode from "./lib/SkNode.svelte";
  import { onMount, setContext } from "svelte";
  import { writable } from "svelte/store";
  import { load, processUpdate } from "./lib/common.js";

  let nodes = writable(new Map());

  let loaded = false;

  setContext('nodes', nodes)

  onMount(async () => {
    let response = await load();
    let m = new Map();

    response.forEach((node) => {
      // Temporary: this "expands" each node in the temporary linear UI to the first child
      // of that node.
      node.selectedId = node.children[0];
      m.set(node.id, node)
  });

    nodes.set(m); 

    loaded = true;

  });

  function save(_event) {
    processUpdate(nodes, {"action": "save"})
  }
</script>

<div class="container mx-lg mx-auto px-8 py-4">
  <div class="flex flex-row mb-8">
    <div class="text-emerald-600 text-3xl">Dialog Skein</div>
    <button class="bg-blue-600 font-black text-white rounded-lg p-2 drop-shadow-lg 
    flex-none mx-2 hover:bg-blue-800 hover:drop-shadow-xl"
    on:click={save}
    >Save</button>
  </div>

  {#if loaded}
  <SkNode id={0}/>
  {/if}
 
</div>
