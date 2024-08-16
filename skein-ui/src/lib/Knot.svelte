<script>
    import { getContext, createEventDispatcher } from "svelte";
    import Text from "./Text.svelte";
    import SkButton from "./SkButton.svelte";
    import { postApi } from "./common.js";
    import { deriveChildren  } from "./children";

    const dispatcher = createEventDispatcher();

    // We don't instantiate a Knot until after we have at least one knot.
    const knots = getContext("knots");
    const childNames = getContext("childNames");

    export let id = undefined;

    $: knot = $knots.get(id);
    $: node = knot.node;
    $: label = $node.label || $node.command;
    $: blessEnabled = $node.unblessed && $node.unblessed != "";

    $: children = deriveChildren(childNames, node);


    async function post(payload) {
        let result = await postApi(payload);

        dispatcher("result", result);

        return result;
    }

    function bless() {
        post({ action: "bless", id: id });
    }

    function replay() {
        post({ action: "replay", id: id });
    }

    let newCommand = null;

    async function runNewCommand() {
        const result = await post({
            action: "new-command",
            command: newCommand,
            id: id,
        });

        newCommand = null;

        knot.selectedId = result.new_id;
    }

    async function deleteNode() {
        post({ action: "delete", id: id });
    }
</script>

<div class="flex flex-row bg-slate-100 rounded-md p-2 text-sm">
    <div class="mx-2 my-auto font-bold text-emerald-400">{label}</div>
    <SkButton on:click={replay}>Replay</SkButton>
    <SkButton disabled={!blessEnabled} on:click={bless}>Bless</SkButton>
    <!-- TODO: Make this red, but don't need a modal, because we have undo! -->
    {#if id != 0}
        <SkButton on:click={deleteNode}>Delete</SkButton>
    {/if}
</div>

<div class="flex flex-row text-xs">
    <div class="bg-yellow-50 basis-6/12 mr-2 p-1">
        {#if !$node.response}
            <em>No blessed response</em>
        {/if}
        <Text value={$node.response} />
    </div>
    {#if blessEnabled}
        <div class="bg-yellow-100 p-1">
            <Text value={$node.unblessed} />
        </div>
    {/if}
</div>

<div class="flex flex-row bg-slate-100 rounded-md p-2 mb-8">
    {#each $children as child (child.id)}
        <SkButton
            selected={knot.selectedId == child.id}
            on:click={() => (knot.selectedId = child.id)}
            >{child.label}
        </SkButton>
    {/each}

    <input
        type="text"
        class="ml-4 mr-2 w-full px-2 text-sm"
        placeholder="New command"
        bind:value={newCommand}
        on:change={runNewCommand}
    />
</div>

{#if knot.selectedId}
    <svelte:self id={knot.selectedId} on:result />
{/if}
