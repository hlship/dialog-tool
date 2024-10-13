<script>
    import * as Diff from "diff";

    export let response;
    export let unblessed;

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
    class="bg-yellow-50 w-full whitespace-pre relative p-2">
    <slot name="actions"/>
    {#if unblessed}
        {#each changes as change}
            <span class={spanClass(change)}>{change.value}</span>
        {/each}
    {:else}
        {response}
    {/if}
</div>
