<script>
    import { getContext, createEventDispatcher, onMount } from "svelte";
    import Text from "./Text.svelte";
    import SkButton from "./SkButton.svelte";
    import { postApi } from "./common.js";

    const dispatcher = createEventDispatcher();

    // We don't instantiate an SkNode until after we have at least one node.
    const nodes = getContext("nodes");

    export let id = undefined;

    $: node = $nodes.get(id);
    $: label = node.label || node.command;
    $: blessEnabled = node.unblessed && node.unblessed != "";
    $: children = node.children.map((id) => $nodes.get(id));

    async function post(payload) {
        let result = await postApi(payload);

        dispatcher("result", result );

        return result;
    }

    function bless() {
        post({ action: "bless", id: id });
    }

    function replay() {
        post({action: "replay", id: id});
    }

    let newCommand = null;

    async function runNewCommand() {
        const result = await post({
            action: "new-command",
            command: newCommand,
            id: id,
        });

        newCommand = null;

        node.selectedId = result.new_id;

        // TODO: Find a way to move input to the new text field in the new child node
    }
</script>

<div class="flex flex-row bg-slate-100 rounded-md p-2">
    <div class="mx-2 my-auto font-bold text-emerald-400">{label}</div>
    <SkButton on:click={replay}>Replay</SkButton>
    <SkButton disabled={!blessEnabled} on:click={bless}>Bless</SkButton>
</div>

<div class="flex flex-row">
    <div class="bg-yellow-50 basis-6/12 mr-2 p-1">
        {#if !node.response}
            <em>No blessed response</em>
        {/if}
        <Text value={node.response} />
    </div>
    {#if node.unblessed}
    <div class="bg-yellow-100 p-1">
        <Text value={node.unblessed} />
    </div>
    {/if}
</div>

<div class="flex flex-row bg-slate-100 rounded-md p-2 mb-8">
    {#each children as child (child.id)}
        <SkButton selected={node.selectedId == child.id}
        on:click={() => node.selectedId = child.id}>{child.command}
        </SkButton>
    {/each}

    <input
        type="text"
        class="ml-4 mr-2 w-full px-2"
        placeholder="New command"
        bind:value={newCommand}
        on:change={runNewCommand}
    />
</div>

{#if node.selectedId}
    <svelte:self id={node.selectedId} on:result/>
{/if}
