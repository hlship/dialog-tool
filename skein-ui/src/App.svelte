<script>
  import SkNode from "./lib/SkNode.svelte";
  import { onMount, setContext } from "svelte";
  import { writable } from "svelte/store";

  let nodes = writable(new Map());

  let loaded = false;

  setContext('nodes', nodes)

  onMount(async () => {
    let response = await fetch("//localhost:10140/api");
    let body = await response.json();
    let m = new Map();

    body.forEach((node) => {
      // Temporary: this "expands" each node in the temporary linear UI to the first child
      // of that node.
      node.selectedId = node.children[0];
      m.set(node.id, node)
  });

    nodes.set(m); 

    loaded = true;

  });
</script>

<div class="container mx-lg mx-auto px-8 py-4">
  <h1 class="text-emerald-600 text-3xl">Dialog Skein</h1>


  {#if loaded}
  <SkNode id={0}/>
  {/if}
 
</div>
