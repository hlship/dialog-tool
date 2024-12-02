<script lang="ts">
    import { Modal, Alert } from "flowbite-svelte";
    import { ExclamationCircleSolid } from "flowbite-svelte-icons";

    type Props = {
        message: string,
        running: boolean
    };

    let { message, running = $bindable() }: Props = $props();

</script>

<Modal title="Warning"
    bind:open={running}
    size="sm"
    onclose={() => running = false }>
    <Alert color="red">
        <ExclamationCircleSolid slot="icon" class="w-5 h-5" /> 
        {message}</Alert>
</Modal>
