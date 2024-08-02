<script>
    import { getContext } from "svelte";
    import Text from "./Text.svelte";

    let nodes = getContext("nodes");

    export let id = undefined;

    // Child, if any, selected for this node.
    let childId = null;

    $: node = $nodes.get(id);
    $: label =  node.label || node.command ;
    $: children = node.children;
    $: blessEnabled = node.unblessed && node.unblessed != ""

    let button = "bg-blue-600 font-black text-white rounded-lg p-2 drop-shadow-lg flex-none mx-2";
    let hover = "hover:bg-blue-800 hover:drop-shadow-xl";
    let buttonFull = button + " " + hover;

    $: blessButtonClasses = blessEnabled ? buttonFull : button;

</script>

{@debug node}

<div class="flex flex-row bg-slate-100 rounded-md px-2 py-4">
    <div class="mx-2 font-bold text-emerald-400">{label}</div>
    <button class="{buttonFull}">Replay</button>
    <button class="{blessButtonClasses}" disabled="{! blessEnabled}">Bless</button>
</div>

<div class="flex flex-row mt-2">
    <div class="bg-yellow-50 basis-6/12 mr-2 p-1">
        {#if ! node.response}
        <em>No blessed response</em>
        {/if}
        <Text value={node.response}/>
    </div>
    <div class="bg-yellow-100 p-1">
        <Text value={node.unblessed}/>
    </div>

</div>



