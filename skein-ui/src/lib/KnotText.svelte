<script>
    import { createEventDispatcher } from "svelte";
    import { Button, Tooltip } from "flowbite-svelte";
    import {
        CheckCircleSolid,
        CheckPlusCircleSolid,
    } from "flowbite-svelte-icons";
    import * as Diff from "diff";

    export let response;
    export let unblessed;

    let blessVisible = false;

    const dispatch = createEventDispatcher();

    let changes;

    $: {
        if (response && unblessed) {
            changes = Diff.diffWords(response, unblessed);
        } else if (response == undefined && unblessed) {
            changes = [{ added: true, value: unblessed }];
        }
    }

    function spanClass(change) {
        if (change.added) {
            return "text-blue-700 font-bold";
        }

        if (change.removed) {
            return "text-red-800 font-bold line-through";
        }

        return "";
    }
</script>

<!-- svelte-ignore a11y-no-static-element-interactions -->
<div
    class="bg-yellow-50 w-full p-1 relative whitespace-pre"
    on:mouseenter={() => (blessVisible = true)}
    on:mouseleave={() => (blessVisible = false)}
>
    {#if unblessed}
        {#if blessVisible}
        <div class="absolute top-2 right-2">
            <Button color="blue" size="xs" on:click={() => dispatch("bless")}>
                <CheckCircleSolid class="w-5 h-5 me-2" /> Bless</Button
            >
            <Tooltip>Accept this change</Tooltip>
            <Button color="blue" size="xs" on:click={() => dispatch("blessTo")}>
                <CheckPlusCircleSolid class="w-5 h-5 me-2" /> Bless To</Button
            >
            <Tooltip>Accept all changes from start to here</Tooltip>
        </div>
        {/if}
        {#each changes as change}
            <span class={spanClass(change)}>{change.value}</span>
        {/each}
    {:else}
        {response}
    {/if}
</div>
