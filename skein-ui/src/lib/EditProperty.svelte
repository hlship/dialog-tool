<script lang="ts">
    import { tick } from "svelte";
    import { Modal, Alert } from "flowbite-svelte";
    import { ExclamationCircleSolid } from "flowbite-svelte-icons";

    type Props = {
        title: string;
        value: string;
        change: (newValue: string) => Promise<boolean>,
        error?: string | null,
        help?: string
    };

    let { title, value, change, error, help }: Props = $props();

    let running = $state(false);
    let field;
    let editValue = $state(null);

    export async function activate(): Promise<void> {
        running = true;

        editValue = value;

        await tick();

        field.select();
    }

    async function keydown(event) {
        if (event.code == "Escape") {
            running = false;
            event.preventDefault();
        }

        if (event.code == "Enter") {
            event.preventDefault();

            const close = await change(editValue);

            if (close) 
            { running = false; }
            else {
                field.select();
            }

        }
    }
</script>

<Modal {title} bind:open={running} size="sm" onclose={() => null}>
    {#if error}
    <Alert color="red">
        <ExclamationCircleSolid slot="icon" class="w-5 h-5" /> 
        {error}</Alert>
    {/if}
    <input
        type="text"
        bind:this={field}
        bind:value={editValue}
        onkeydown={keydown}
        class="text-sm w-full"
    />
    {#if help}
    <span class="text-sm text-slate-400">{help}</span>
    {/if}
</Modal>
