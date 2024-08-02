<script>
    import { getContext } from "svelte";

    let nodes = getContext("nodes");

    export let id = undefined;

    $: node = $nodes.get(id);
    $: label =  node.label || node.command ;
    $: children = node.children;
</script>

<div>
    <div>
        <div>{id}: {label}</div>
    </div>

    <div class="ml-10">
        {#each children as child (child)}
            <svelte:self id={child} />
        {/each}
    </div>
</div>
