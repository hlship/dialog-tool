<script lang="ts">
    import { tick } from "svelte";
    import { Modal } from "flowbite-svelte";

    type Props = {
        title: string;
        value: string;
        change: (newValue: string) => void;
    };

    let { title, value, change }: Props = $props();

    let running = $state(false);
    let field;
    let editValue = $state(null);

    export async function activate(): Promise<void> {
        running = true;

        editValue = value;

        await tick();

        field.select();
    }

    function keydown(event) {
        if (event.code == "Escape") {
            running = false;
            event.preventDefault();
        }

        if (event.code == "Enter") {
            running = false;
            event.preventDefault();

            change(editValue);
        }
    }
</script>

<Modal {title} bind:open={running} size="sm" onclose={() => null}>
    <input
        type="text"
        bind:this={field}
        bind:value={editValue}
        onkeydown={keydown}
        class="text-sm w-full"
    />
</Modal>
