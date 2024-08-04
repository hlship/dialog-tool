<script>
    import { getContext } from "svelte";
    import Text from "./Text.svelte";
    import SkButton from "./SkButton.svelte";
    import { processUpdate } from "./common.js";

    let nodes = getContext("nodes");

    export let id = undefined;

    $: node = $nodes.get(id);
    $: label = node.label || node.command;
    $: blessEnabled = node.unblessed && node.unblessed != "";
    $: children = node.children.map((id) => $nodes.get(id));

    async function update(payload) {
        return await processUpdate(nodes, payload);
    }

    async function bless() {
        await update({ action: "bless", id: id });
    }

    let newCommand = null;

    async function runNewCommand() {
        const result = await update({
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
    <SkButton>Replay</SkButton>
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
    <svelte:self id={node.selectedId} />
{/if}
