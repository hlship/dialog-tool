<script>
    import { createEventDispatcher } from "svelte";
    import { Button, Tooltip } from "flowbite-svelte";
import { CheckCircleSolid, CheckPlusCircleSolid } from "flowbite-svelte-icons"

export let response;
export let unblessed;

let blessVisible = false;

const dispatch = createEventDispatcher();

</script>


    <div class="bg-yellow-50 basis-6/12 mr-2 p-1 whitespace-pre">
        {#if response}
            {response}
        {:else}
            <em>No blessed response</em>
        {/if}
    </div>
    {#if unblessed}
        <!-- svelte-ignore a11y-no-static-element-interactions -->
        <div
            class="bg-yellow-100 basis-6/12 p-1 relative whitespace-pre"
            on:mouseenter={() => (blessVisible = true)}
            on:mouseleave={() => (blessVisible = false)}
        >
            {#if blessVisible}
                <div class="absolute top-2 right-2">
                    <Button color="blue" size="xs" on:click={() => dispatch("bless") }>
                        <CheckCircleSolid class="w-5 h-5 me-2" /> Bless</Button
                    >
                    <Tooltip>Accept this change</Tooltip>
                    <Button color="blue" size="xs" on:click={() => dispatch("blessTo") }>
                        <CheckPlusCircleSolid class="w-5 h-5 me-2" /> Bless To</Button
                    >
                    <Tooltip>Accept all changes from start to here</Tooltip>
                </div>
            {/if}
            {unblessed}
        </div>
    {/if}
