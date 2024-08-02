<script>
  import SkNode from "./lib/SkNode.svelte";
  import { onMount } from "svelte";

  let nodes;

  onMount(async () => {
    let response = await fetch("//localhost:10140/api");
    nodes = await response.json();
  });
</script>

<div class="container mx-lg mx-auto px-8 py-4 d">
  <h1 class="text-emerald-600 text-3xl">Dialog Skein</h1>

  <p>This is a whole lot of text about the Skein, and how you use it, and how to navigate
    and how you use it for testing, and how it's neat as sin, and a great idea that
    I can't take credit for.
  </p>
 
  {#if nodes}
  <ul>
    {#each nodes as node (node.id)}
      <li><SkNode {node} /></li>
    {/each}
  </ul>
  {/if}
</div>
