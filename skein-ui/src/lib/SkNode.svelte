<script>
    import { getContext } from "svelte";
    import Text from "./Text.svelte";

    let nodes = getContext("nodes");

    export let id = undefined;

    $: node = $nodes.get(id);
    $: label = node.label || node.command;
    $: blessEnabled = node.unblessed && node.unblessed != "";

    let button =
        "bg-blue-600 font-black text-white rounded-lg p-2 drop-shadow-lg flex-none mx-2";
    let hover = "hover:bg-blue-800 hover:drop-shadow-xl";
    let buttonFull = button + " " + hover;

    $: blessButtonClasses = blessEnabled ? buttonFull : button;

    $: children = node.children.map((id) => $nodes.get(id));

    function applyResult(result) {
        result.updates.forEach((n) => nodes.update((m) => m.set(n.id, n)));
        // TODO: Deletions
    }

    async function processUpdate(payload) {
        const response = await fetch("//localhost:10140/api", {
            method: "POST",
            cache: "no-cache",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        });

        const result = await response.json();

        applyResult(result);

        return result;
    }

    async function bless(_event) {
        await processUpdate({ action: "bless", id: id });
    }

    let newCommand = null;

    async function runNewCommand(_event) {
        const result = await processUpdate({
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
    <button class={buttonFull}>Replay</button>
    <button class={blessButtonClasses} disabled={!blessEnabled} on:click={bless}
        >Bless</button
    >
</div>

<div class="flex flex-row">
    <div class="bg-yellow-50 basis-6/12 mr-2 p-1">
        {#if !node.response}
            <em>No blessed response</em>
        {/if}
        <Text value={node.response} />
    </div>
    <div class="bg-yellow-100 p-1">
        <Text value={node.unblessed} />
    </div>
</div>

<div class="flex flex-row bg-slate-100 rounded-md p-2 mb-8">
    {#each children as child (child.id)}
        <button class={button} on:click={(_) => (node.selectedId = child.id)}
            >{child.command}</button
        >
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
