<script lang="ts">
    import * as Diff from "diff";
	import type { Snippet } from 'svelte';

    interface Props {
        response : string, 
        unblessed : string | undefined,
        actions : Snippet
    }


    let { response, unblessed, actions } : Props = $props();

    function computeChanges(response : string, unblessed : string | undefined) : Diff.Change[] {
        if (response && unblessed) {
            return  Diff.diffWords(response, unblessed);
        } else if (response == undefined && unblessed) {
            return  [{ added: true, removed: false, value: unblessed }];
        }
        else { return []; }
    }

    let changes = $derived(computeChanges(response, unblessed));

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

<div
    class="bg-yellow-50 w-full whitespace-pre relative p-2">
    {@render actions()}
    {#if unblessed}
        {#each changes as change}
            <span class={spanClass(change)}>{change.value}</span>
        {/each}
    {:else}
        {response}
    {/if}
</div>
