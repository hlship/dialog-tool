<script>
  import SkNode from "./lib/SkNode.svelte";
  import { onMount, setContext, getContext } from "svelte";
  import { writable } from "svelte/store";

  let nodes = writable(new Map());

  let loaded = false;

  setContext('nodes', nodes)

  nodes.subscribe((m) => console.log(`${m.size} nodes`))

  onMount(async () => {
    let response = await fetch("//localhost:10140/api");
    let body = await response.json();
    let m = new Map();

    body.forEach((node) => m.set(node.id, node));

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
