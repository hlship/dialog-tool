<script>
    import { getContext, createEventDispatcher, onMount } from "svelte";
    import { postApi } from "./common.js";
    import { deriveChildren } from "./children";
    import { Button } from "flowbite-svelte";

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

    let commandField;
    let blessVisible = false;

    onMount(() => {
        commandField.focus();
        commandField.scrollIntoView();
    });

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
    <Button size="xs" color="blue" on:click={replay}>Replay</Button>
    <!-- TODO: Make this red, but don't need a modal, because we have undo! -->
    {#if id != 0}
        <Button class="ml-2" size="xs" color="blue" on:click={deleteNode}
            >Delete</Button
        >
    {/if}
</div>

<div class="flex flex-row text-xs">
    <div class="bg-yellow-50 basis-6/12 mr-2 p-1 whitespace-pre">
        {#if $node.response}
        {$node.response}
        {:else}
            <em>No blessed response</em>
        {/if}
    </div>
    {#if blessEnabled}
        <!-- svelte-ignore a11y-no-static-element-interactions -->
        <div
            class="bg-yellow-100 basis-6/12 p-1 relative whitespace-pre"
            on:mouseenter={() => blessVisible = true}
            on:mouseleave={() => blessVisible = false} >
            {#if blessVisible}
                <div class="absolute top-2 right-2">
                    <Button color="blue" size="xs" on:click={bless}
                        >Bless</Button
                    >
                </div>
            {/if}
            {$node.unblessed}
        </div>
    {/if}
</div>

<div class="flex flex-wrap bg-slate-100 rounded-md p-2 mb-2 text-nowrap">
    {#each $children as child (child.id)}
        <Button
            class="m-1"
            pill
            color={knot.selectedId == child.id ? "green" : "blue"}
            size="xs"
            on:click={() => (knot.selectedId = child.id)}
            >{child.label}
        </Button>
    {/each}
    <input
        type="text"
        bind:this={commandField}
        class="ml-2 w-1/4 grow px-2 text-sm"
        placeholder="New command"
        bind:value={newCommand}
        on:change={runNewCommand}
    />
</div>

{#if knot.selectedId}
    <svelte:self id={knot.selectedId} on:result />
{/if}
