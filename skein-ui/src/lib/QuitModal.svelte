<script lang="ts">
    import { postApi } from "./common.svelte";
    import { Modal, Button } from "flowbite-svelte";

interface Props {
    dirty : boolean,
    running : boolean
}

let { dirty, running = $bindable() } : Props = $props()

async function quit () {
    await postApi({action: "quit"});

    window.close()
}

async function save() {
    await postApi({action: "save"});

    quit()
}

</script>

<Modal title="Quit"
bind:open={running}
size="sm">
        Really quit? 
        {#if dirty}
        <p>
        Unsaved progress will be lost.
        </p>
        {/if}
    <svlete:fragment slot="footer">
        <Button color="blue" on:click={ () => running = false }>Cancel</Button>
        {#if dirty} 
        <Button color="blue" on:click={ save }>Save First</Button>
        {/if}
        <Button color={ dirty ? 'red' : 'blue'}>Quit</Button>
    </svlete:fragment>
</Modal>