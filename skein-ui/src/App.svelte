<script>
  import SkNode from './lib/SkNode.svelte'

  let nodes = [{"id": 1, "command": "get help"}]

  async function load_nodes() {
    let response = await fetch("//localhost:10140/api")
    nodes = await response.json()
  }

  load_nodes()

</script>

<h1>Dialog Skein</h1>

<ul>
{#each nodes as node (node.id)}
  <li><SkNode {node}/></li>
{/each}
</ul>



